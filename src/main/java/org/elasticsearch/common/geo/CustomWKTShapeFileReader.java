package org.elasticsearch.common.geo;

import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link ShapeFileReader} which supports Shapes being defined in a custom
 * version of WKT which adds the name of the Shape as a prefix.  An example of the definition
 * of a Shape is "New Zealand: ENVELOPE (-176.848755 -34.414718, 178.841063  -52.578055)".
 * Note that ":" is used as a delimiter between name and WKT Shape definition.
 */
public class CustomWKTShapeFileReader extends AbstractComponent implements ShapeFileReader {

    public CustomWKTShapeFileReader(Settings settings) {
        super(settings);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Shape> readFiles(File shapeDirectory) throws IOException {
        final WKTShapeParser shapeParser = new WKTShapeParser();
        final Map<String, Shape> shapesByName = new HashMap<String, Shape>();

        for (File file : shapeDirectory.listFiles()) {
            if (file.isDirectory()) {
              shapesByName.putAll(readFiles(file));
            } else {
                LineProcessor<Void> lineProcessor = new LineProcessor<Void>() {

                    @Override
                    public boolean processLine(String line) throws IOException {
                        try {
                            int delimiter = line.indexOf(':');
                            if (delimiter == -1) {
                                logger.error("Delimiter ':' missing from line [{}]", line);
                                return false;
                            }
                            String name = line.substring(0, delimiter);
                            Shape shape = shapeParser.parse(line.substring(delimiter + 1));
                            shapesByName.put(name, shape);
                            return true;
                        } catch (ParseException pe) {
                            logger.error("ParseException thrown while parsing Shape definition", pe);
                            return false;
                        }
                    }

                    @Override
                    public Void getResult() {
                        return null;
                    }
                };

                Files.readLines(file, Charset.forName("UTF-8"), lineProcessor);
            }
        }

        return shapesByName;
    }
}
