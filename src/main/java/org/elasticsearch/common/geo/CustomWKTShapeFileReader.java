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

public class CustomWKTShapeFileReader extends AbstractComponent implements ShapeFileReader {

    public CustomWKTShapeFileReader(Settings settings) {
        super(settings);
    }

    public Map<String, Shape> readFile(File file) throws IOException {
        final WKTShapeParser shapeParser = new WKTShapeParser();

        LineProcessor<Map<String, Shape>> lineProcessor = new LineProcessor<Map<String, Shape>>() {

            Map<String, Shape> shapesByName = new HashMap<String, Shape>();

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
            public Map<String, Shape> getResult() {
                return shapesByName;
            }
        };

        return Files.readLines(file, Charset.forName("UTF-8"), lineProcessor);
    }
}
