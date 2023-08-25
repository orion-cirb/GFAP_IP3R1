package GFAP_NeuN_IP3R1_Tools;

import GFAP_NeuN_IP3R1_Tools.Cellpose.CellposeSegmentImgPlusAdvanced;
import GFAP_NeuN_IP3R1_Tools.Cellpose.CellposeTaskSettings;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.RGBStackMerge;
import ij.plugin.filter.Analyzer;
import ij.process.AutoThresholder;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.DoubleAccumulator;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;


/**
 * @author Philippe Mailly & Héloïse Monnet
 */
public class Tools {
    
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    private final String helpUrl = "https://github.com/orion-cirb/GFAP_IP3R1";
    
    private final CLIJ2 clij2 = CLIJ2.getInstance();
    
    String[] chNames = {"Cells", "IP3R1 dots"};
    public Calibration cal = new Calibration();
    public double pixVol;
    
    // Astrocytes detection
    public boolean detectAstro = true;
    public String astroThMethod = "Triangle";
    
    // Neurons detection (if detectAstro is false)
    public String cellposeEnvDir = IJ.isWindows()? System.getProperty("user.home")+File.separator+"miniconda3"+File.separator+"envs"+File.separator+"CellPose" : "/opt/miniconda3/envs/cellpose";
    public String cellposeModel = "cyto2";
    public int cellposeDiam = 200;
    public double cellposeStitchTh = 1;
    
    public double minCellVol = 2;
    
    // Dots detection
    public String dotsThMethod = "Otsu";
    public double minDotsVol = 0.02;
    
    
    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Find images extension
     */
    public String findImageType(String imagesFolder) {
        String ext = "";
        String[] files = new File(imagesFolder).list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
                case "nd" :
                   ext = fileExt;
                   break;
                case "nd2" :
                   ext = fileExt;
                   break;
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "lsm" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;
                case "tiff" :
                    ext = fileExt;
                    break;
            }
        }
        return(ext);
    }

        
    /**
     * Find images in folder
     */
    public ArrayList findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt) && !f.startsWith("."))
                images.add(imagesFolder + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public Calibration findImageCalib(IMetadata meta) {
        // read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
    /**
     * Find channels name
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels(String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n).toString();
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelFluor(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelFluor(0, n).toString();
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = meta.getChannelEmissionWavelength(0, n).value().toString();
                break;    
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = meta.getChannelEmissionWavelength(0, n).value().toString();
                break; 
            default :
                for (int n = 0; n < chs; n++)
                    channels[n] = Integer.toString(n);
        }
        return(channels);     
    }
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String imagesDir, String[] channels) {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 80, 0);
        gd.addImage(icon);
        
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chName: chNames) {
            gd.addChoice(chName+": ", channels, channels[index]);
            index++;
        }
        
        String[] thMethods = AutoThresholder.getMethods();
        gd.addMessage("Cells detection", Font.getFont("Monospace"), Color.blue);
        gd.addCheckbox(" Detect astrocytes (otherwise neurons)", detectAstro);
        gd.addChoice("Astro threshold method: ", thMethods, astroThMethod);
        gd.addNumericField("Min volume (µm3): ", minCellVol, 2);
        
        gd.addMessage("Dots detection", Font.getFont("Monospace"), Color.blue);
        gd.addChoice("Threshold method: ", thMethods, dotsThMethod);
        gd.addNumericField("Min volume (µm3): ", minDotsVol, 2);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY calibration (µm): ", cal.pixelHeight, 3);
        gd.addNumericField("Z calibration (µm): ", cal.pixelDepth, 3);
        gd.addHelp(helpUrl);
        gd.showDialog();
        
        String[] chChoices = new String[chNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        
        detectAstro = gd.getNextBoolean();
        astroThMethod = gd.getNextChoice();
        minCellVol = gd.getNextNumber();
        
        dotsThMethod = gd.getNextChoice();
        minDotsVol = gd.getNextNumber();
        
        cal.pixelHeight = cal.pixelWidth = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelHeight*cal.pixelWidth*cal.pixelDepth;
        
        if (gd.wasCanceled())
            chChoices = null;
        return(chChoices);
    }
    
    
    /**
     * Flush and close an image
     */
    public void closeImage(ImagePlus img) {
        img.flush();
        img.close();
    }

       
    /**
     * Detect cells
     */
    public Objects3DIntPopulation detectCells(ImagePlus imgIn, ArrayList<Roi> rois) {
        ImagePlus imgOut;
        if(detectAstro) {
            ImagePlus imgMed = median2D(imgIn, 4);
            imgOut = threshold(imgMed, astroThMethod);
            closeImage(imgMed);
        } else {
            // Preprocessing
            ImagePlus imgMed = median2D(imgIn, 2);

            // Define CellPose settings
            CellposeTaskSettings settings = new CellposeTaskSettings(cellposeModel, 1, cellposeDiam, cellposeEnvDir);
            settings.setStitchThreshold(cellposeStitchTh);
            settings.useGpu(true);

            // Run Cellpose
            CellposeSegmentImgPlusAdvanced cellpose = new CellposeSegmentImgPlusAdvanced(settings, imgMed);
            imgOut = cellpose.run();
            closeImage(imgMed);
        }
        imgOut.setCalibration(cal);
        
        // Fill ROIs in black
        if (!rois.isEmpty())
            fillImg(imgOut, rois);
        
        Objects3DIntPopulation cellsPop = getPopFromImage(imgOut);
        System.out.println("Nb cellular objects detected:"+cellsPop.getNbObjects());
        popFilterSize(cellsPop, minCellVol, Double.MAX_VALUE);
        System.out.println("Nb cellular objects remaining after size filtering: "+ cellsPop.getNbObjects());
        
        closeImage(imgOut);
        return(cellsPop);
    }
    
    
    /**
     * 2D median filtering using CLIJ2
     */ 
    public ImagePlus median2D(ImagePlus img, double sizeXY) {
       ClearCLBuffer imgCL = clij2.push(img); 
       ClearCLBuffer imgCLMed = clij2.create(imgCL);
       clij2.median3DSliceBySliceBox(imgCL, imgCLMed, sizeXY, sizeXY);
       ImagePlus imgMed = clij2.pull(imgCLMed);
       clij2.release(imgCL);
       clij2.release(imgCLMed);
       return(imgMed);
    }
    
    
    /**
     * Automatic thresholding using CLIJ2
     */
    public ImagePlus threshold(ImagePlus img, String thMed) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        ImagePlus imgBin = clij2.pull(imgCLBin);
        clij2.release(imgCL);
        clij2.release(imgCLBin);
        return(imgBin);
    }
    
      
    /**
     * Fill ROIs in black in image
     */
    public ImagePlus fillImg(ImagePlus img, ArrayList<Roi> rois) {
        img.getProcessor().setColor(Color.BLACK);
        for (int s = 1; s <= img.getNSlices(); s++) {
            img.setSlice(s);
            for (Roi r : rois) {
                img.setRoi(r);
                img.getProcessor().fill(img.getRoi());
            }
        }
        img.deleteRoi();
        return(img);
    } 
    
    
    /**
     * Return population of 3D objects population from binary image
     */
    private Objects3DIntPopulation getPopFromImage(ImagePlus img) {
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
        labels.closeImagePlus();
        return(pop);
    }
    
    
    /**
     * Remove objects in population with size < min and size > max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.setVoxelSizeXY(cal.pixelWidth);
        pop.setVoxelSizeZ(cal.pixelDepth);
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }
      

    /**
     * Detect dots
     */
    public Objects3DIntPopulation detectDots(ImagePlus imgIn, ArrayList<Roi> rois) {
        ImagePlus imgDOG = DOG(imgIn, 1, 5);
        ImagePlus imgBin = threshold(imgDOG, dotsThMethod);
        imgBin.setCalibration(cal);
        
        // Fill ROIs in black
        if (!rois.isEmpty())
            fillImg(imgBin, rois);
        
        Objects3DIntPopulation dotsPop = getPopFromImage(imgBin);
        System.out.println("Nb dots detected:"+dotsPop.getNbObjects());
        popFilterSize(dotsPop, minDotsVol, Double.MAX_VALUE);
        System.out.println("Nb dots remaining after size filtering: "+ dotsPop.getNbObjects());
        
        closeImage(imgDOG);
        closeImage(imgBin);
        
        return(dotsPop);
    }

    
    /**
     * Difference of Gaussians filtering using CLIJ2
     */ 
    public ImagePlus DOG(ImagePlus img, double size1, double size2) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, size1, size1, size1, size2, size2, size2);
        ImagePlus imgDOG = clij2.pull(imgCLDOG);
        clij2.release(imgCL);
        clij2.release(imgCLDOG);
        return(imgDOG);
    }
    
    
    /**
     * Find dots inside and outside astrocytes
     */
    public List<Objects3DIntPopulation> findDotsInOutCells(Objects3DIntPopulation dotsPop, Objects3DIntPopulation cellsPop, ImagePlus imgDots) {
        ImageHandler imhDots = ImageHandler.wrap(imgDots).createSameDimensions();
        dotsPop.drawInImage(imhDots);
        
        ImageHandler imhDotsOut = imhDots.duplicate();
        for (Object3DInt cell: cellsPop.getObjects3DInt()) {
            cell.drawObject(imhDotsOut, 0);
        }
        Objects3DIntPopulation popOut = new Objects3DIntPopulation(imhDotsOut);
        
        ImagePlus imgSub = new ImageCalculator().run("subtract stack create", imhDots.getImagePlus(), imhDotsOut.getImagePlus());
        Objects3DIntPopulation popIn = new Objects3DIntPopulation(ImageHandler.wrap(imgSub));
        
        imhDots.closeImagePlus();
        imhDotsOut.closeImagePlus();
        return(Arrays.asList(popIn, popOut));  
    }
    
    /**
     * Compute ROIs total volume
     */
    public double getRoisVolume(ArrayList<Roi> rois, ImagePlus img) {
        double roisVol = 0;
        for(Roi roi: rois) {
            PolygonRoi poly = new PolygonRoi(roi.getFloatPolygon(), Roi.FREEROI);
            poly.setLocation(0, 0);
            
            img.resetRoi();
            img.setRoi(poly);

            ResultsTable rt = new ResultsTable();
            Analyzer analyzer = new Analyzer(img, Analyzer.AREA, rt);
            analyzer.measure();
            roisVol += rt.getValue("Area", 0);
        }

        return(roisVol * img.getNSlices() * cal.pixelDepth);
    }
    
    
    /**
     * Find total volume of objects in population
     */
    public double findPopVolume(Objects3DIntPopulation pop) {
        DoubleAccumulator sumVol = new DoubleAccumulator(Double::sum,0.d);
        pop.getObjects3DInt().parallelStream().forEach(obj -> { 
            sumVol.accumulate(new MeasureVolume(obj).getVolumeUnit());
        });
        return(sumVol.doubleValue());
    }
    
    
    /**
     * Draw results
     */
    public void drawResults(Objects3DIntPopulation cellsPop, Objects3DIntPopulation dotsInCellsPop, Objects3DIntPopulation dotsOutCellsPop, 
            ImagePlus imgCells, ImagePlus imgDots, String name) {
        ImageHandler imhCells = ImageHandler.wrap(imgCells).createSameDimensions();
        ImageHandler imhDotsIn = imhCells.createSameDimensions();
        ImageHandler imhDotsOut = imhCells.createSameDimensions();
        
        // Draw cells pop in blue, dotsIn pop in red and dotsOut pop in green
        for (Object3DInt cell: cellsPop.getObjects3DInt()) 
            cell.drawObject(imhCells, 255);
        for (Object3DInt dot: dotsInCellsPop.getObjects3DInt()) 
            dot.drawObject(imhDotsIn, 255);
        for (Object3DInt dot: dotsOutCellsPop.getObjects3DInt()) 
            dot.drawObject(imhDotsOut, 255);

        ImagePlus[] imgColors = {imhDotsIn.getImagePlus(), imhDotsOut.getImagePlus(), imhCells.getImagePlus(), imgDots, imgCells};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");
        
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(name); 
        
        imhCells.closeImagePlus();
        imhDotsIn.closeImagePlus();
        imhDotsOut.closeImagePlus();
    }
    
}
