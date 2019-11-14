package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.plugin.interfaces.AbstractStepPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

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

    public void createPlaceholderImages() {

        try {
            Path folder = Paths.get(myStep.getProzess().getImagesOrigDirectory(false));
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
            Helper.setFehlerMeldung("Cannot find image folder");
            return;
        }
        int number = Integer.parseInt(numberOfPages);
        for (int i = 1; i <=number; i++) {
            log.info("Create image " + i);
        }

        if (number == 1) {
            Helper.setMeldung("Created 1 image.");
        } else {
            Helper.setMeldung("Created "+  number + " images.");
        }
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
