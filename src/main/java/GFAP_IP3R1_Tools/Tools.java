package GFAP_IP3R1_Tools;


import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
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
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;



public class Tools {
        
    // min size for objects
    public double minDots = 0.5;
    public double minAstro = 5;
    // max size for objects
    public final double maxDots = Double.MAX_VALUE;
    public final double maxAstro = Double.MAX_VALUE;
    public Calibration cal = new Calibration();
    public double pixVol;
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
    private final CLIJ2 clij2 = CLIJ2.getInstance();
    
     /**
     * check  installed modules
     * @return 
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
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
               case "nd" :
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
            System.out.println("No Image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    /**
     * Dialog
     */
    public String[] dialog(String imagesDir, String[] channels) {
        String[] chNames = {"Astrocyte : ", "IP3R1 : "};
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 15, 0);
        gd.addImage(icon);
        gd.addMessage("Channels selection", Font.getFont("Monospace"), Color.blue);
        for (int n = 0; n < chNames.length; n++) {
            gd.addChoice(chNames[n], channels, channels[n]);
        }
        gd.addMessage("Size filter", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min astrocyte size (µm3) : ", minAstro, 2);
        gd.addNumericField("Min dots size (µm3) : ", minDots, 2);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("xy calibration (µm)", cal.pixelHeight,3);
        gd.addNumericField("z calibration (µm)", cal.pixelDepth,3);
        gd.showDialog();
        String[] chChoices = new String[chNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        if (gd.wasCanceled())
                chChoices = null;
        minAstro = gd.getNextNumber();
        minDots = gd.getNextNumber();
        cal.pixelHeight = gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight;
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelHeight*cal.pixelWidth*cal.pixelDepth;
        return(chChoices);
    } 
    
     /**
     * Find channels name and None to end of list
     * @param imageName
     * @param meta
     * @param reader
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs+1];
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
        channels[chs] = "None";
        return(channels);     
    }
    

    public void setCalibration(ImagePlus imp) {
        imp.setCalibration(cal);
    }
     
    /**
     * Find image calibration
     * @param meta
     * @return 
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
        System.out.println("x cal = " +cal.pixelWidth+", z cal=" + cal.pixelDepth);
        return(cal);
    }
    
      public Objects3DIntPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        labels.setCalibration(cal);
        Objects3DIntPopulation pop = new Objects3DIntPopulation(labels);
        return pop;
    }
      
      /**
     * Remove object with size < min and size > max
     * @param pop
     * @param min
     * @param max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.setVoxelSizeXY(cal.pixelWidth);
        pop.setVoxelSizeZ(cal.pixelDepth);
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }
      
    /**
     * 3D Median filter using CLIJ2
     * @param img
     * @param sizeXY
     * @param sizeZ
     * @return 
     */ 
    public ImagePlus median3D_filter(ImagePlus img, double sizeXY, double sizeZ) {
       ClearCLBuffer imgCL = clij2.push(img); 
       ClearCLBuffer imgCLMed = clij2.create(imgCL);
       clij2.median3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
       clij2.release(imgCL);
       ImagePlus imgMed = clij2.pull(imgCLMed);
        clij2.release(imgCLMed);
       return(imgMed);
    }
    
    /**
     *
     * @param img
     */
    public void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Save images
     * @param astroObj
     * @param dotsInsideGFAP
     * @param dotsOutsideGFAP
     * @param imgs
     * @return  
     */
    public void saveImageObjects(Object3DInt astroObj, Objects3DIntPopulation dotsInsideGFAP, Objects3DIntPopulation dotsOutsideGFAP, 
            ImagePlus imgAstro, ImagePlus imgDots, String name) {
        ImageHandler imhAstro = ImageHandler.wrap(imgAstro).createSameDimensions();
        astroObj.drawObject(imhAstro);
        ImageHandler imhDotsInside = imhAstro.createSameDimensions();
        dotsInsideGFAP.drawInImage(imhDotsInside);
        ImageHandler imhDotsOutside = imhAstro.createSameDimensions();
        dotsOutsideGFAP.drawInImage(imhDotsOutside);
        ImagePlus[] imgColors = {imhAstro.getImagePlus(), imhDotsInside.getImagePlus(), imhDotsOutside.getImagePlus(), imgAstro, imgDots};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(imgAstro.getCalibration());
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(name); 
        imhAstro.closeImagePlus();
        imhDotsInside.closeImagePlus();
        imhDotsOutside.closeImagePlus();
    }
    
    /**
     * Reset labels of the objects composing a population
     * @param pop
     */
    public void resetLabels(Objects3DPopulation pop) {
        for(int i=0; i < pop.getNbObjects(); i++) {
            pop.getObject(i).setValue(i+1);
        }
    }
    
     /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param img
     * @param size1
     * @param size2
     * @return imgGauss
     */ 
    public ImagePlus DOG(ImagePlus img, double size1, double size2) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, size1, size1, size1, size2, size2, size2);
        clij2.release(imgCL);
        ImagePlus imgDOG = clij2.pull(imgCLDOG);
        clij2.release(imgCLDOG);
        return(imgDOG);
    }
    
    /**
     * Threshold 
     * USING CLIJ2
     * @param img
     * @param thMed
     * @return 
     */
    public ImagePlus threshold(ImagePlus img, String thMed) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        clij2.release(imgCL);
        ImagePlus imgBin = clij2.pull(imgCLBin);
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
     * Find Astrocyte with DOG and threshold
     * @param imgIn
     * @param rois
     * @return 
     */
    public Object3DInt detectAstrocyte(ImagePlus imgIn, ArrayList<Roi> rois) {
        ImagePlus imgDOG = DOG(imgIn, 4, 6);
        ImagePlus imgBin = threshold(imgDOG, "Triangle");
        // Fill ROIs in black
        if (!rois.isEmpty()) {
            fillImg(imgBin, rois);
        }
        closeImages(imgDOG);
        imgBin.setCalibration(cal);
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(imgBin));
        closeImages(imgBin);
        Objects3DIntPopulation astroPop = new Objects3DIntPopulation(labels);
        labels.closeImagePlus();
        System.out.println(astroPop.getNbObjects()+" astrocytes before size filtering");
        popFilterSize(astroPop, minAstro, maxAstro);
        System.out.println(astroPop.getNbObjects()+" astrocytes after size filtering");
        // get pop as one 3D Object
        ImageHandler imhAstro = ImageHandler.wrap(imgIn).createSameDimensions();
        for (Object3DInt obj : astroPop.getObjects3DInt())
            obj.drawObject(imhAstro, 255);
        Object3DInt astroObj = new Object3DInt(imhAstro); 
        return(astroObj);
    }
    
    /*
    Detect dots population
    */
    public Objects3DIntPopulation detectDots(ImagePlus imgIn, ArrayList<Roi> rois) {
        ImagePlus imgDog = DOG(imgIn, 2, 4);
        ImagePlus imgBin = threshold(imgDog, "Moments");
        closeImages(imgDog);
        // Fill ROIs in black
        if (!rois.isEmpty()) {
            fillImg(imgBin, rois);
        }
        imgBin.setCalibration(cal);
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(imgBin));
        closeImages(imgBin);
        Objects3DIntPopulation dotsPop = new Objects3DIntPopulation(labels);
        labels.closeImagePlus();
        System.out.println(dotsPop.getNbObjects()+" IP3R1 dots before size filtering");
        popFilterSize(dotsPop, minDots, maxDots);
        System.out.println(dotsPop.getNbObjects()+" IP3R1 dots after size filtering");
        return(dotsPop);
    }
    
    public ImagePlus fillOutsideObj(Object3DInt obj, ImagePlus img) {
        //IJ.setForegroundColor(0, 0, 0);
        ImagePlus imgFill = new Duplicator().run(img);
        ImageHandler imh = ImageHandler.wrap(img).createSameDimensions();
        obj.drawObject(imh, 255);
        ThresholdToSelection tts = new ThresholdToSelection();
        ImagePlus mask = imh.getImagePlus();
        for (int z = 1; z <= img.getNSlices(); z++) {
            mask.setSlice(z);
            IJ.setAutoThreshold(mask, "Default dark no-reset");            
            tts.setup("", mask);
            tts.run(mask);
            Roi roi = tts.convert(mask.getProcessor());
            imgFill.setSlice(z);
            imgFill.setRoi(roi);
            IJ.run(imgFill, "Clear", "slice");
            imgFill.updateAndDraw();
        }
        closeImages(mask);
        imh.closeImagePlus();
        return(imgFill);
    }
    
    /**
     * Find dots inside and outside of astrocytes
     * @param dotsPop
     * @param astroObj
     * @param img
     * @return 
     */
    public List<Objects3DIntPopulation> findDotsInOutAstro(Objects3DIntPopulation dotsPop, Object3DInt astroObj, ImagePlus imgAstro) {
        // dots outside
        ImageHandler imhDots = ImageHandler.wrap(imgAstro).createSameDimensions();
        dotsPop.drawInImage(imhDots);
        ImageHandler imhDotsOut = imhDots.duplicate();
        ImagePlus imgDotsDraw = imhDots.getImagePlus();
        astroObj.drawObject(imhDotsOut, 0);
        Objects3DIntPopulation popOut = new Objects3DIntPopulation(imhDotsOut);
        // dots inside
        ImagePlus imgFill = fillOutsideObj(astroObj, imgDotsDraw);
        Objects3DIntPopulation popIn = new Objects3DIntPopulation(ImageHandler.wrap(imgFill));
        imhDots.closeImagePlus();
        closeImages(imgFill);
        closeImages(imgDotsDraw);
        return(Arrays.asList(popIn, popOut));  
    }

    /**
     * Find sum vessel volume
     */
    public double findPopVolume(Objects3DIntPopulation pop) {
        DoubleAccumulator sumVol = new DoubleAccumulator(Double::sum,0.d);
        pop.getObjects3DInt().parallelStream().forEach(obj -> { 
            sumVol.accumulate(new MeasureVolume(obj).getVolumeUnit());
        });
        return(sumVol.doubleValue());
    }
    
}
