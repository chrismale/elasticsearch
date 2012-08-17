package org.elasticsearch.test.unit.common.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.geo.ShapeBuilder;
import org.elasticsearch.common.geo.WKTShapeParser;
import org.testng.annotations.Test;

import java.text.ParseException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * Tests for {@link WKTShapeParser}
 */
public class WKTShapeParserTests {

    private static final WKTShapeParser SHAPE_PARSER = new WKTShapeParser();

    @Test
    public void testParsePoint() throws ParseException {
        assertParses("POINT (100 90)", ShapeBuilder.newPoint(100, 90));
        assertParses("POINT ( 100 90 )", ShapeBuilder.newPoint(100, 90));
        assertParses("POINT(100 90)", ShapeBuilder.newPoint(100, 90));
        assertParses("POINT (-45 90 )", ShapeBuilder.newPoint(-45, 90));
        assertParses("POINT (-45.3 90.4 )", ShapeBuilder.newPoint(-45.3, 90.4));
    }

    @Test
    public void testParsePoint_invalidDefinitions() {
        try {
            SHAPE_PARSER.parse("POINT (100 90");
            fail("ParseException expected");
        } catch (ParseException e) {
        }

        try {
            SHAPE_PARSER.parse("POINT 100 90)");
            fail("ParseException expected");
        } catch (ParseException e) {
        }

        try {
            SHAPE_PARSER.parse("POINT (100)");
            fail("ParseException expected");
        } catch (ParseException e) {
        }

        try {
            SHAPE_PARSER.parse("POINT (10f0 90)");
            fail("ParseException expected");
        } catch (ParseException e) {
        }
    }

    @Test
    public void testParsePolygon() throws ParseException {
        Shape polygonNoHoles = ShapeBuilder.newPolygon()
                .point(100, 0)
                .point(101, 0)
                .point(101, 1)
                .point(100, 1)
                .point(100, 0)
                .build();
        assertParses("POLYGON ((100 0, 101 0, 101 1, 100 1, 100 0))", polygonNoHoles);
        assertParses("POLYGON((100 0,101 0,101 1,100 1,100 0))", polygonNoHoles);

        Shape polygonWithHoles = ShapeBuilder.newPolygon()
                .point(100, 0)
                .point(101, 0)
                .point(101, 1)
                .point(100, 1)
                .point(100, 0)
                .newHole()
                    .point(100.2, 0.2)
                    .point(100.8, 0.2)
                    .point(100.8, 0.8)
                    .point(100.2, 0.8)
                    .point(100.2, 0.2)
                .endHole()
                .build();
        assertParses("POLYGON ((100 0, 101 0, 101 1, 100 1, 100 0), (100.2 0.2, 100.8 0.2, 100.8 0.8, 100.2 0.8, 100.2 0.2))", polygonWithHoles);
    }

    private void assertParses(String wkt, Shape expected) throws ParseException {
        assertEquals(SHAPE_PARSER.parse(wkt), expected);
    }
}
