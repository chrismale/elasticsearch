package org.elasticsearch.common.geo;

import com.spatial4j.core.shape.Shape;
import com.spatial4j.core.shape.jts.JtsGeometry;
import com.spatial4j.core.shape.simple.PointImpl;
import com.spatial4j.core.shape.simple.RectangleImpl;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WKTShapeParser {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final String rawString;
    private int offset;

    public WKTShapeParser(String rawString) {
        this.rawString = rawString.toLowerCase(Locale.ENGLISH);
    }

    public Shape parse() throws IOException {
        if (rawString.startsWith("point")) {
            return parsePoint();
        } else if (rawString.startsWith("polygon")) {
            return parsePolygon();
        } else if (rawString.startsWith("multipolygon")) {
            return parseMulitPolygon();
        } else if (rawString.startsWith("envelope")) {
            return parseEnvelope();
        }

        throw new IllegalArgumentException("Unknown Shape type defined in [" + rawString + "]");
    }

    private Shape parsePoint() throws IOException {
        offset = 5;
        if (nextCharNoWS() == '(') {
            offset++;
            Coordinate coordinate = coordinate();

            if (nextCharNoWS() == ')') {
                return new PointImpl(coordinate.x, coordinate.y);
            }
        }

        throw new IllegalArgumentException();
    }

    private Shape parsePolygon() throws IOException {
        offset = 7;
        return new JtsGeometry(polygon());
    }

    private Polygon polygon() throws IOException {
        List<Coordinate[]> coordinateSequenceList = coordinateSequenceList();

        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coordinateSequenceList.get(0));

        LinearRing[] holes = null;
        if (coordinateSequenceList.size() > 1) {
            holes = new LinearRing[coordinateSequenceList.size() - 1];
            for (int i = 1; i < coordinateSequenceList.size(); i++) {
                holes[i - 1] = GEOMETRY_FACTORY.createLinearRing(coordinateSequenceList.get(i));
            }
        }
        return GEOMETRY_FACTORY.createPolygon(shell, holes);
    }

    private Shape parseMulitPolygon() throws IOException {
        offset = 12;
        List<Polygon> polygons = new ArrayList<Polygon>();
        if (nextCharNoWS() == '(') {
            offset++;
            polygons.add(polygon());
            while (nextCharNoWS() == ',') {
                offset++;
                polygons.add(polygon());
            }
            if (nextCharNoWS() == ')') {
                offset++;
                return new JtsGeometry(
                        GEOMETRY_FACTORY.createMultiPolygon(polygons.toArray(new Polygon[polygons.size()])));
            }
        }

        throw new IllegalArgumentException();
    }

    private Shape parseEnvelope() throws IOException {
        offset = 8;
        Coordinate[] coordinateSequence = coordinateSequence();
        return new RectangleImpl(coordinateSequence[0].x, coordinateSequence[1].x,
                coordinateSequence[1].y, coordinateSequence[0].y);
    }

    private List<Coordinate[]> coordinateSequenceList() throws IOException {
        List<Coordinate[]> sequenceList = new ArrayList<Coordinate[]>();

        if (nextCharNoWS() == '(') {
            offset++;
            sequenceList.add(coordinateSequence());
            while (nextCharNoWS() == ',') {
                offset++;
                sequenceList.add(coordinateSequence());
            }
            if (nextCharNoWS() == ')') {
                offset++;
                return sequenceList;
            }
        }

        throw new IllegalArgumentException();
    }

    private Coordinate[] coordinateSequence() throws IOException {
        List<Coordinate> sequence = new ArrayList<Coordinate>();

        if (nextCharNoWS() == '(') {
            offset++;
            sequence.add(coordinate());
            while (nextCharNoWS() == ',') {
                offset++;
                sequence.add(coordinate());
            }
            if (nextCharNoWS() == ')') {
                offset++;
                return sequence.toArray(new Coordinate[sequence.size()]);
            }
        }

        throw new IllegalArgumentException();
    }

    private Coordinate coordinate() throws IOException {
        char c = nextCharNoWS();
        double x = number();

        c = nextCharNoWS();
        double y = number();

        return new Coordinate(x, y);
    }

    private double number() throws IOException {
        int startOffset = offset;
        for (char c = rawString.charAt(offset); offset < rawString.length(); c = rawString.charAt(++offset)) {
            if (!(Character.isDigit(c) || c == '.' || c == '-')) {
                return Double.parseDouble(rawString.substring(startOffset, offset));
            }
        }

        throw new EOFException();
    }

    private char nextCharNoWS() throws IOException {
        while (offset < rawString.length()) {
            if (!Character.isWhitespace(rawString.charAt(offset))) {
                return rawString.charAt(offset);
            }
            offset++;
        }
        throw new EOFException();
    }
}
