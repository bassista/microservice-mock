package net.technolords.micro.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.xpath.XPathExpressionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.technolords.micro.config.jaxb.Configuration;
import net.technolords.micro.config.jaxb.Configurations;
import net.technolords.micro.config.jaxb.resource.Complex;
import net.technolords.micro.config.jaxb.resource.Group;
import net.technolords.micro.config.jaxb.resource.Simple;

/**
 * Created by Technolords on 2016-Jul-20.
 */
public class ConfigurationManager {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    public static final String HTTP_POST = "POST";
    private static final String PATH_TO_CONFIG_FILE = "config/configuration.xml";
    private Configurations configurations = null;
    private XpathEvaluator xpathEvaluator = null;
    private Map<String, Configuration> getConfigurations = new HashMap<>();
    private Map<String, Configuration> postConfigurations = new HashMap<>();

    /**
     * Auxiliary method to find a response for a given get request, based on the path.
     *
     * @param path
     *  The path associated with the get request.
     * @return
     *  The response associated with the get request.
     *
     * @throws JAXBException
     *  When reading the configuration fails.
     */
    public String findResponseForGetOperationWithPath(String path) throws JAXBException, IOException {
        this.initializeConfig();
        LOGGER.debug("About to find response for get operation with path: {}", path);
        if (this.getConfigurations.containsKey(path)) {
            LOGGER.debug("... found, proceeding to the data part...");
            Configuration configuration = this.getConfigurations.get(path);
            Simple resource = configuration.getGetResource();
            if (resource.getCachedData() != null) {
                return resource.getCachedData();
            } else {
                // Load and update cache
                LOGGER.debug("About to load data from: {}", resource.getResource());
                return this.readResourceAndUpdateCacheData(resource);
            }
        }
        LOGGER.debug("... not found!");
        return null;
    }

    /**
     * Auxiliary method to find a response for a given post request, based on the path and the message (body).
     *
     * @param path
     *  The path associated with the post request.
     * @param message
     *  The message associated with the post request.
     *
     * @return
     *  The response associated with the post request.
     *
     * @throws IOException
     *  When reading the response fails.
     * @throws XPathExpressionException
     *  When evaluation the xpath expression fails.
     * @throws JAXBException
     *  When reading the configuration fails.
     */
    public String findResponseForPostOperationWithPathAndMessage(String path, String message) throws IOException, XPathExpressionException, JAXBException {
        this.initializeConfig();
        LOGGER.debug("About to find response for post operation with path: {}", path);
        if (this.postConfigurations.containsKey(path)) {
            LOGGER.debug("... found, proceeding to the data part...");
            Configuration configuration = this.postConfigurations.get(path);
            // Iterate of the resource, and verify whether the xpath matches with the data
            Group group = configuration.getPostResources();
            for (Complex complex : group.getResources()) {
                if (complex.getXpath() != null) {
                    LOGGER.debug("... found xpath: {}", complex.getXpath().getXpath());
                    if (this.xpathEvaluator.evaluateXpathExpression(complex.getXpath().getXpath(), message, configuration)) {
                        LOGGER.debug("... xpath matched, about to find associated resource");
                        return this.readResourceAndUpdateCacheData(complex.getResource());
                    }
                } else {
                    LOGGER.debug("No xpath configured, about to load the data from: {}", complex.getResource().getResource());
                    return this.readResourceAndUpdateCacheData(complex.getResource());
                }
            }
        }
        LOGGER.debug("... not found!");
        return null;
    }

    /**
     * Auxiliary method to initialize the configuration manager, which means:
     * - reading and parsing the xml configuration
     * - instantiating a xpath evaluator
     *
     * @throws JAXBException
     *  When parsing the XML configuration file fails.
     */
    protected void initializeConfig() throws JAXBException {
        if (this.configurations == null) {
            LOGGER.debug("About to initialize resources from configuration file...");
            Unmarshaller unmarshaller = JAXBContext.newInstance(Configurations.class).createUnmarshaller();
            this.configurations = (Configurations) unmarshaller.unmarshal(this.getClass().getClassLoader().getResourceAsStream(PATH_TO_CONFIG_FILE));
            LOGGER.debug("Total loaded resources: {}", this.configurations.getConfigurations().size());
            for (Configuration configuration : this.configurations.getConfigurations()) {
                if (HTTP_POST.equals(configuration.getType().toUpperCase())) {
                    // Add resource to post configuration group
                    this.postConfigurations.put(configuration.getUrl(), configuration);
                } else {
                    // Add resource to get configuration group
                    this.getConfigurations.put(configuration.getUrl(), configuration);
                }
            }
            LOGGER.debug("URL mappings completed [{} for post, {} for get]", this.postConfigurations.size(), this.getConfigurations.size());
        }
        if (this.xpathEvaluator == null) {
            this.xpathEvaluator = new XpathEvaluator();
        }
    }

    /**
     * Auxiliary method that reads the response data as well as updating the internal cache so
     * subsequent reads will will served from memory.
     *
     * @param resource
     *  The resource to read and cache.
     *
     * @return
     *  The data associated with the resource (i.e. response).
     *
     * @throws IOException
     *  When reading the resource fails.
     */
    private String readResourceAndUpdateCacheData(Simple resource) throws IOException {
        InputStream fileStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource.getResource());
        LOGGER.debug("Path to file exists: {}",  fileStream.available());
        resource.setCachedData(new BufferedReader(new InputStreamReader(fileStream)).lines().collect(Collectors.joining("\n")));
        return resource.getCachedData();
    }

}
