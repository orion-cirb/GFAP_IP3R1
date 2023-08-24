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
import mcib3d.geom2.Objects3DIntPopulation;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;


/**
* Detect GFAP astrocytes with median filtering + thresholding
* Detect IP3R1 dots with DoG filtering + thresholding
* Distinguish dots inside from dots outside astrocytes
* Compute volume of astrocytes, dots inside and dots outside astrocytes
* @author Philippe Mailly & Héloïse Monnet
*/
public class GFAP_IP3R1 implements PlugIn {

    private GFAP_IP3R1_Tools.Tools tools = new Tools();
       
    public void run(String arg) {
        try {
            if ((!tools.checkInstalledModules())) {
                return;
            }
            
            String imageDir = IJ.getDirectory("Choose images directory")+File.separator;
            if (imageDir == null) {
                return;
            }
            
            // Find images with fileExt extension
            String fileExt = tools.findImageType(imageDir);
            ArrayList<String> imageFiles = tools.findImages(imageDir, fileExt);
            if (imageFiles.isEmpty()) {
                IJ.showMessage("Error", "No images found with " + fileExt + " extension");
                return;
            }
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find image calibration
            tools.findImageCalib(meta);
            
            // Find channel names
            String[] channelNames = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Generate dialog box
            String[] channels = tools.dialog(imageDir, channelNames);
            if (channels == null) {
                IJ.showStatus("Plugin canceled");
                return;
            }
            
            // Create output folder
            String outDirResults = imageDir + File.separator + "Results_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write headers results for results files
            FileWriter fwResults = new FileWriter(outDirResults + "Results.xls", false);
            BufferedWriter results = new BufferedWriter(fwResults);
            results.write("Image name\tImage vol (µm3)\tImage-ROI vol (µm3)\tAstrocytes volume (µm3)\t"
                    + "IP3R1 dots volume inside astrocytes (µm3)\tIP3R1 dots volume outside astrocytes (µm3)\n");
            results.flush();
            
            for (String f: imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                tools.print("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Check if ROIs file exists, keep rois to clear regions containing "artefacts"
                String roiName = imageDir + File.separator + rootName; 
                roiName = new File(roiName + ".zip").exists() ? roiName + ".zip" : roiName + ".roi";
                ArrayList<Roi> rois = new ArrayList<>();
                if (new File(roiName).exists()) {
                    RoiManager rm = new RoiManager(false);
                    rm.reset();
                    rm.runCommand("Open", roiName);
                    Collections.addAll(rois, rm.getRoisAsArray());
                }
                
                // Analyze GFAP astrocytes channel
                tools.print("- Analyzing GFAP astrocytes channel -");
                int indexCh = ArrayUtils.indexOf(channelNames, channels[0]);
                ImagePlus imgAstro = BF.openImagePlus(options)[indexCh];
                Objects3DIntPopulation astroPop = tools.detectAstrocytes(imgAstro, rois);
                
                // Analyze IP3R1 dots channel
                tools.print("- Analyzing IP3R1 dots channel -");
                indexCh = ArrayUtils.indexOf(channelNames, channels[1]);
                ImagePlus imgDots = BF.openImagePlus(options)[indexCh];
                Objects3DIntPopulation dotsPop = tools.detectDots(imgDots, rois);
                
                // Find dots inside and outside astrocytes
                tools.print("- Finding dots inside and outside astrocytes -");
                List<Objects3DIntPopulation> dotsInOutAstro = tools.findDotsInOutAstro(dotsPop, astroPop, imgDots);
                Objects3DIntPopulation dotsInAstroPop = dotsInOutAstro.get(0);
                Objects3DIntPopulation dotsOutAstroPop = dotsInOutAstro.get(1);
                
                // Write results
                tools.print("- Writing and drawing results -");
                double imgVol = imgDots.getWidth() * imgDots.getHeight() * imgDots.getNSlices() * tools.pixVol;
                double roisVol = tools.getRoisVolume(rois, imgDots);
                results.write(rootName+"\t"+imgVol+"\t"+(imgVol-roisVol)+"\t"+tools.findPopVolume(astroPop)+"\t"+
                        tools.findPopVolume(dotsInAstroPop)+"\t"+tools.findPopVolume(dotsOutAstroPop)+"\n");
                results.flush();
                
                // Draw results
                tools.drawResults(astroPop, dotsInAstroPop, dotsOutAstroPop, imgAstro, imgDots, outDirResults+rootName+".tif");
                tools.closeImage(imgDots);
                tools.closeImage(imgAstro);
            }
            results.close();
        } catch (IOException | DependencyException | ServiceException | FormatException ex) {
            Logger.getLogger(GFAP_IP3R1.class.getName()).log(Level.SEVERE, null, ex);
        }
        tools.print("All done!");
    }
}
