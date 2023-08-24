# GFAP_IP3R1

* **Developed for:** Anaïs
* **Team:** Rouach
* **Date:** August 2023
* **Software:** Fiji

### Images description

3D images taken with a x60 objective

2 channels:
  1. *Alexa Fluor 488:* GFAP astrocytes
  2. *Alexa Fluor 647:* IP3R1 dots

With each image can be provided a *.roi* or *.zip* file containing one or multiple ROI(s).

### Plugin description

* Detect GFAP astrocytes with median filtering + thresholding
* Detect IP3R1 dots with DoG filtering + thresholding
* Distinguish dots inside from dots outside astrocytes
* Compute total volume of astrocytes and of each population of dots
* If ROI(s) provided, remove from the analysis astrocytes and dots that are inside

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ2** Fiji plugin

### Version history

Version 1 released on August 24, 2023.
