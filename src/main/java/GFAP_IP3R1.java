/*
* Detect Astrocyte with DOG + Huang thresholding + median filtering
* Detect IP3R1 dots with median and Triangle threshold
* Detect dots inside and outside Astrocyte and processes
* Compute volume astrocytes/processes, dots inside and outside
 * Author Philippe Mailly / Heloïse Monnet
 */


import GFAP_IP3R1_Tools.Tools;
import ij.*;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureVolume;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;


public class GFAP_IP3R1 implements PlugIn {

    private final boolean canceled = false;
    private String imageDir = "";
    private GFAP_IP3R1_Tools.Tools tools = new Tools();
    
    
    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        try {
            if (canceled) {
                IJ.showMessage(" Pluging canceled");
                return;
            }
            String imageDir = IJ.getDirectory("Choose images directory")+File.separator;
            if (imageDir == null) {
                return;
            }
            // Find images with fileExt extension
            String fileExt = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles.isEmpty()) {
                IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                return;
            }
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            
            // Find chanels, image calibration
            reader.setId(imageFiles.get(0));
            String[] channelNames = tools.findChannels(imageFiles.get(0), meta, reader);
            tools.findImageCalib(meta);
            String[] channels = tools.dialog(imageDir, channelNames);
            if(channels == null)
                return;
            
            // Create output folder
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            Date date = new Date();
            String outDirResults = imageDir + File.separator+ "Results_"+dateFormat.format(date) +  File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write headers results for results files
            FileWriter results = new FileWriter(outDirResults + "Results.xls", false);
            BufferedWriter outPutResults = new BufferedWriter(results);
            outPutResults.write("ImageName\tAstrocyte volume (µm3)\tIP3R1 dots volume inside astrocyte (µm3)"
                    + "\tIP3R1 dots volume outside astrocyte (µm3)\n");
            outPutResults.flush();
            
            // Do astrocyte detection
            IJ.setForegroundColor(255, 255, 255);
            IJ.setBackgroundColor(0, 0, 0);

            System.out.println("Segmentations ...");
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f); 
                // Check if rois file exist, keep rois to clear regions containing "artefacts"
                ArrayList<Roi> rois = new ArrayList<>();
                String roiRootName = imageDir + File.separator + rootName; 
                String roiName = new File(roiRootName + ".zip").exists() ? roiRootName + ".zip" : roiRootName + ".roi";
                if (new File(roiName).exists()) {
                    RoiManager rm = new RoiManager(false);
                    if (rm != null)
                        rm.reset();
                    else
                        rm = new RoiManager(false);
                    rm.runCommand("Open", roiName);
                    Collections.addAll(rois, rm.getRoisAsArray());
                }
                reader.setId(f);
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Open GFAP channel (Astrocyte)
                int indexCh = ArrayUtils.indexOf(channelNames, channels[0]);
                ImagePlus imgAstro = BF.openImagePlus(options)[indexCh];
                Object3DInt astroObj = tools.detectAstrocyte(imgAstro, rois);
                double astroVol = new MeasureVolume(astroObj).getVolumeUnit();
                
                // Open IP3R1 channel (dots)
                indexCh = ArrayUtils.indexOf(channelNames, channels[1]);
                ImagePlus imgDots = BF.openImagePlus(options)[indexCh];
                Objects3DIntPopulation dotsPop = tools.detectDots(imgDots, rois);
                
                // find dots inside / outside astrocyte
                System.out.println("Finding dots inside / outside astrocyte ....");
                List<Objects3DIntPopulation> dotsInOutAstro = tools.findDotsInOutAstro(dotsPop,astroObj, imgAstro);
                Objects3DIntPopulation dotsInsideGFAP = dotsInOutAstro.get(0);
                double dotsInsideVol = tools.findPopVolume(dotsInsideGFAP);
                Objects3DIntPopulation dotsOutsideGFAP = dotsInOutAstro.get(1);
                double dotsOutsideVol = tools.findPopVolume(dotsOutsideGFAP);
                // print results
                outPutResults.write(rootName+"\t"+astroVol+"\t"+dotsInsideVol+"\t"+dotsOutsideVol+"\n");
                outPutResults.flush();
                // save images objects
                tools.saveImageObjects(astroObj, dotsInsideGFAP, dotsOutsideGFAP, imgAstro, imgDots, outDirResults+"_"+rootName+"_Objects.tif");
                tools.closeImages(imgDots);
                tools.closeImages(imgAstro);
                
            }
            outPutResults.close();

        } catch (IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(GFAP_IP3R1.class.getName()).log(Level.SEVERE, null, ex);
        }
            IJ.showStatus("Process done");
    }
}
