package de.intranda.goobi.plugins;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;
import javax.imageio.ImageIO;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.plugin.interfaces.AbstractStepPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j
public class PlaceholderCreationPlugin extends AbstractStepPlugin implements IStepPlugin, IPlugin {

    private static final String PLUGIN_NAME = "intranda_step_placeholder-creation";

    @Getter
    @Setter
    private String numberOfPages;

    private static char[] numbers = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

    @Override
    public void initialize(Step step, String returnPath) {
        super.returnPath = returnPath;
        super.myStep = step;
    }

    public void createPlaceholderImages() throws IOException {
        Path folder = null;
        try {
            folder = Paths.get(myStep.getProzess().getImagesOrigDirectory(false));
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
            Helper.setFehlerMeldung("Cannot find image folder");
            return;
        }

        ConfigurationHelper config = ConfigurationHelper.getInstance();
        BufferedImage im = ImageIO.read(Paths.get(config.getGoobiFolder(), "xslt", "placeholder.png").toFile());
        int number = Integer.parseInt(numberOfPages);
        for (int i = 1; i <= number; i++) {
            log.info("Create image " + i);
            renderPageNumImage(i, im, folder);
        }

        try {

            Prefs prefs = myStep.getProzess().getRegelsatz().getPreferences();
            DocStructType newPage = prefs.getDocStrctTypeByName("page");
            // read metadata
            Fileformat ff = myStep.getProzess().readMetadataFile();
            DigitalDocument digitalDocument = ff.getDigitalDocument();
            // create pagination
            DocStruct physicaldocstruct = digitalDocument.getPhysicalDocStruct();
            DocStruct logical = digitalDocument.getLogicalDocStruct();
            if (logical.getType().isAnchor()) {
                if (logical.getAllChildren() != null && logical.getAllChildren().size() > 0) {
                    logical = logical.getAllChildren().get(0);
                }
            }

            Metadata md = new Metadata(prefs.getMetadataTypeByName("NumberOfImages"));
            md.setValue(numberOfPages);
            logical.addMetadata(md);

            MetadataType MDTypeForPath = prefs.getMetadataTypeByName("pathimagefiles");
            if (physicaldocstruct == null) {
                DocStructType dst = prefs.getDocStrctTypeByName("BoundBook");
                physicaldocstruct = digitalDocument.createDocStruct(dst);
                digitalDocument.setPhysicalDocStruct(physicaldocstruct);

                try {
                    List<? extends Metadata> filepath = physicaldocstruct.getAllMetadataByType(MDTypeForPath);
                    if (filepath == null || filepath.isEmpty()) {
                        Metadata mdForPath = new Metadata(MDTypeForPath);
                        if (SystemUtils.IS_OS_WINDOWS) {
                            mdForPath.setValue("file:/" + folder.toString());
                        } else {
                            mdForPath.setValue("file://" + folder.toString());
                        }
                        physicaldocstruct.addMetadata(mdForPath);
                    }
                } catch (Exception e) {
                    log.error(e);
                }
            }

            List<String> filenames = StorageProvider.getInstance().list(folder.toString());
            MetadataType physicalType = prefs.getMetadataTypeByName("physPageNumber");
            MetadataType logicalType = prefs.getMetadataTypeByName("logicalPageNumber");
            MetadataType identifierType = prefs.getMetadataTypeByName("ImageIdentifier");

            DecimalFormat decimalFormat = new DecimalFormat("####");

            int currentPhysicalOrder = 1;
            for (String filename : filenames) {
                DocStruct dsPage = digitalDocument.createDocStruct(newPage);
                // physical page no
                physicaldocstruct.addChild(dsPage);

                Metadata pyhsicalPageNo = new Metadata(physicalType);
                pyhsicalPageNo.setValue(String.valueOf(currentPhysicalOrder));
                dsPage.addMetadata(pyhsicalPageNo);

                // logical page no

                Metadata logicalPagNo = new Metadata(logicalType);
                logicalPagNo.setValue("uncounted");
                dsPage.addMetadata(logicalPagNo);

                // identifier
                Metadata identifier = new Metadata(identifierType);
                identifier.setValue(myStep.getProzess().getTitel() + "_" + decimalFormat.format(currentPhysicalOrder));
                dsPage.addMetadata(identifier);

                // link image to main docstruct
                logical.addReferenceTo(dsPage, "logical_physical");

                // image name
                ContentFile cf = new ContentFile();
                if (SystemUtils.IS_OS_WINDOWS) {
                    cf.setLocation("file:/" + folder.toString() + "/" + filename);
                } else {
                    cf.setLocation("file://" + folder.toString() + "/" + filename);
                }
                dsPage.addContentFile(cf);

                currentPhysicalOrder = currentPhysicalOrder + 1;
            }

            myStep.getProzess().writeMetadataFile(ff);
        } catch (ReadException | PreferencesException | WriteException | InterruptedException | SwapException | DAOException
                | TypeNotAllowedForParentException | TypeNotAllowedAsChildException | MetadataTypeNotAllowedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (number == 1) {
            Helper.setMeldung("Created 1 image.");
        } else {
            Helper.setMeldung("Created " + number + " images.");
        }
    }

    private void renderPageNumImage(int i, BufferedImage im, Path folder) throws IOException {
        String renderString = Integer.toString(i);
        Graphics2D g2d = im.createGraphics();
        g2d.setColor(new Color(0.231f, 0.518f, 0.773f));
        g2d.fillRect(0, im.getHeight() - 401, im.getWidth() - 20, 400);
        //g2d.setColor(new Color(0.702f, 0.702f, 0.702f));
        g2d.setColor(Color.WHITE);
        Font font = new Font("OpenSans", Font.PLAIN, 60);
        g2d.setFont(font);
        int width = g2d.getFontMetrics().stringWidth(renderString);
        g2d.drawString(renderString, (im.getWidth() / 2) - (width / 2), im.getHeight() - 165);
        g2d.dispose();
        ImageIO.write(im, "TIFF", folder.resolve(String.format("%08d.tif", i)).toFile());
    }

    static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    public void numericValidator(FacesContext context, UIComponent component, Object value) {
        String data = String.valueOf(value);
        FacesMessage message = null;
        boolean valid = true;

        if (StringUtils.isBlank(data)) {
            valid = false;
            message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Missing data", "Field is empty.");
        } else {
            if (!StringUtils.containsOnly(data, numbers)) {
                valid = false;
                message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Missing data", "Only numbers are allowed");
            } else {
                try {
                    int number = Integer.parseInt(data);
                    if (number == 0) {
                        valid = false;
                        message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Missing data", "Enter a number higher than 0.");
                    }
                } catch (Exception e) {
                    valid = false;
                    message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Missing data", "Value cannot be parsed to a number");
                }
            }
        }
        if (!valid) {
            throw new ValidatorException(message);
        }
    }

    @Override
    public boolean execute() {
        return false;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.PART;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    @Override
    public String getDescription() {
        return PLUGIN_NAME;
    }
}