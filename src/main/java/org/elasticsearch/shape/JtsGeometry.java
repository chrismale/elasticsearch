package org.elasticsearch.shape;

import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.context.jts.JtsSpatialContext;
import com.spatial4j.core.exception.InvalidShapeException;
import com.spatial4j.core.shape.Circle;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.Shape;
import com.spatial4j.core.shape.Point;
import com.spatial4j.core.shape.SpatialRelation;
import com.spatial4j.core.shape.jts.JtsPoint;
import com.spatial4j.core.shape.simple.PointImpl;
import com.spatial4j.core.shape.simple.RectangleImpl;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import com.vividsolutions.jts.operation.valid.IsValidOp;

import java.util.ArrayList;
import java.util.List;

// PORT FROM UNRELEASED SPATIAL4J 3.0.  MOVE TO RELEASED VERSION WHEN AVAILABLE
public class JtsGeometry implements Shape {
    private final Geometry geom;//cannot be a direct instance of GeometryCollection as it doesn't support relate()
    private final boolean hasArea;
    private final Rectangle bbox;

    public JtsGeometry(Geometry geom, JtsSpatialContext ctx) {
        //GeometryCollection isn't supported in relate()
        if (geom.getClass().equals(GeometryCollection.class))
            throw new IllegalArgumentException("JtsGeometry does not support GeometryCollection but does support its subclasses.");

        //NOTE: All this logic is fairly expensive. There are some short-circuit checks though.
        if (ctx.isGeo()) {
            //Unwraps the geometry across the dateline so it exceeds the standard geo bounds (-180 to +180).
            unwrapDateline(geom);//potentially modifies geom
            //If given multiple overlapping polygons, fix it by union
            geom = unionGeometryCollection(geom);//returns same or new geom
            Envelope unwrappedEnv = geom.getEnvelopeInternal();

            //Cuts an unwrapped geometry back into overlaid pages in the standard geo bounds.
            geom = cutUnwrappedGeomInto360(geom);//returns same or new geom
            assert geom.getEnvelopeInternal().getWidth() <= 360;
            assert ! geom.getClass().equals(GeometryCollection.class) : "GeometryCollection unsupported";//double check

            //note: this bbox may be sub-optimal. If geom is a collection of things near the dateline on both sides then
            // the bbox will needlessly span most or all of the globe longitudinally.
            // TODO so consider using MultiShape's planned minimal geo bounding box algorithm once implemented.
            double envWidth = unwrappedEnv.getWidth();
            //makeRect() will adjust minX and maxX considering the dateline and world wrap
            bbox = ctx.makeRect(unwrappedEnv.getMinX(),unwrappedEnv.getMinX() + envWidth,
                    unwrappedEnv.getMinY(),unwrappedEnv.getMaxY());
        } else {//not geo
            Envelope env = geom.getEnvelopeInternal();
            bbox = new RectangleImpl(env.getMinX(),env.getMaxX(),env.getMinY(),env.getMaxY());
        }
        geom.getEnvelopeInternal();//ensure envelope is cached internally, which is lazy evaluated. Keeps this thread-safe.

        //Check geom validity; use helpful error
        // TODO add way to conditionally skip at your peril later
        IsValidOp isValidOp = new IsValidOp(geom);
        if (!isValidOp.isValid())
            throw new InvalidShapeException(isValidOp.getValidationError().toString());
        this.geom = geom;

        this.hasArea = !((geom instanceof Lineal) || (geom instanceof Puntal));
    }

    public static SpatialRelation intersectionMatrixToSpatialRelation(IntersectionMatrix matrix) {
        if (matrix.isContains())
            return SpatialRelation.CONTAINS;
        else if (matrix.isCoveredBy())
            return SpatialRelation.WITHIN;
        else if (matrix.isDisjoint())
            return SpatialRelation.DISJOINT;
        return SpatialRelation.INTERSECTS;
    }

    //----------------------------------------
    //----------------------------------------

    @Override
    public boolean hasArea() {
        return hasArea;
    }

    @Override
    public Rectangle getBoundingBox() {
        return bbox;
    }

    @Override
    public JtsPoint getCenter() {
        return new JtsPoint(geom.getCentroid());
    }

    @Override
    public SpatialRelation relate(Shape other, SpatialContext ctx) {
        if (other instanceof Point)
            return relate((Point)other, ctx);
        else if (other instanceof Rectangle)
            return relate((Rectangle) other, ctx);
        else if (other instanceof Circle)
            return relate((Circle) other, ctx);
        else if (other instanceof JtsGeometry)
            return relate((JtsGeometry) other);
        return other.relate(this, ctx).transpose();
    }

    public SpatialRelation relate(Point pt, SpatialContext ctx) {
        //TODO if not jtsPoint, test against bbox to avoid JTS if disjoint
        JtsPoint jtsPoint = (JtsPoint) (pt instanceof JtsPoint ? pt : ctx.makePoint(pt.getX(), pt.getY()));
        return geom.disjoint(jtsPoint.getJtsPoint()) ? SpatialRelation.DISJOINT : SpatialRelation.CONTAINS;
    }

    public SpatialRelation relate(Rectangle rectangle, SpatialContext ctx) {
        SpatialRelation bboxR = bbox.relate(rectangle,ctx);
        if (bboxR == SpatialRelation.WITHIN || bboxR == SpatialRelation.DISJOINT)
            return bboxR;
        Geometry oGeom = ((JtsSpatialContext)ctx).getGeometryFrom(rectangle);
        return intersectionMatrixToSpatialRelation(geom.relate(oGeom));
    }

    public SpatialRelation relate(Circle circle, SpatialContext ctx) {
        SpatialRelation bboxR = bbox.relate(circle,ctx);
        if (bboxR == SpatialRelation.WITHIN || bboxR == SpatialRelation.DISJOINT)
            return bboxR;

        //Test each point to see how many of them are outside of the circle.
        //TODO consider instead using geom.apply(CoordinateSequenceFilter) -- maybe faster since avoids Coordinate[] allocation
        Coordinate[] coords = geom.getCoordinates();
        int outside = 0;
        int i = 0;
        for (Coordinate coord : coords) {
            i++;
            SpatialRelation sect = circle.relate(new PointImpl(coord.x, coord.y), ctx);
            if (sect == SpatialRelation.DISJOINT)
                outside++;
            if (i != outside && outside != 0)//short circuit: partially outside, partially inside
                return SpatialRelation.INTERSECTS;
        }
        if (i == outside) {
            return (relate(circle.getCenter(), ctx) == SpatialRelation.DISJOINT)
                    ? SpatialRelation.DISJOINT : SpatialRelation.CONTAINS;
        }
        assert outside == 0;
        return SpatialRelation.WITHIN;
    }

    public SpatialRelation relate(JtsGeometry jtsGeometry) {
        Geometry oGeom = jtsGeometry.geom;
        //don't bother checking bbox since geom.relate() does this already
        return intersectionMatrixToSpatialRelation(geom.relate(oGeom));
    }

    @Override
    public String toString() {
        return geom.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JtsGeometry that = (JtsGeometry) o;
        return geom.equalsExact(that.geom);//fast equality for normalized geometries
    }

    @Override
    public int hashCode() {
        //FYI if geometry.equalsExact(that.geometry), then their envelopes are the same.
        return geom.getEnvelopeInternal().hashCode();
    }

    public Geometry getGeom() {
        return geom;
    }

    /**
     * If <code>geom</code> spans the dateline, then this modifies it to be a valid JTS geometry that extends
     * to the right of the standard -180 to +180 width such that some points are greater than +180
     * but some remain less.
     * Takes care to invoke
     * {@link com.vividsolutions.jts.geom.Geometry#geometryChanged()} if needed.
     *
     * @param geom
     * @return The number of times the geometry spans the dateline.  >= 0
     */
    private static int unwrapDateline(Geometry geom) {
        if (geom.getEnvelopeInternal().getWidth() < 180)
            return 0;//can't possibly cross the dateline
        final int[] result = {0};//an array so that an inner class can modify it.
        geom.apply(new GeometryFilter() {
            @Override
            public void filter(Geometry geom) {
                int cross = 0;
                if (geom instanceof LineString) {//note: LinearRing extends LineString
                    if (geom.getEnvelopeInternal().getWidth() < 180)
                        return;//can't possibly cross the dateline
                    cross = unwrapDateline((LineString) geom);
                } else if (geom instanceof Polygon) {
                    if (geom.getEnvelopeInternal().getWidth() < 180)
                        return;//can't possibly cross the dateline
                    cross = unwrapDateline((Polygon) geom);
                } else
                    return;
                result[0] = Math.max(result[0],cross);
            }
        });//geom.apply()

        int crossings = result[0];
        return crossings;
    }

    /** See {@link #unwrapDateline(Geometry)}. */
    private static int unwrapDateline(Polygon poly) {
        LineString exteriorRing = poly.getExteriorRing();
        int cross = unwrapDateline(exteriorRing);
        if (cross > 0) {
            for(int i = 0; i < poly.getNumInteriorRing(); i++) {
                LineString innerLineString = poly.getInteriorRingN(i);
                unwrapDateline(innerLineString);
                for(int shiftCount = 0; ! exteriorRing.contains(innerLineString); shiftCount++) {
                    if (shiftCount > cross)
                        throw new IllegalArgumentException("The inner ring doesn't appear to be within the exterior: "
                                +exteriorRing+" inner: "+innerLineString);
                    shiftGeomByX(innerLineString, 360);
                }
            }
            poly.geometryChanged();
        }
        return cross;
    }

    /** See {@link #unwrapDateline(Geometry)}. */
    private static int unwrapDateline(LineString lineString) {
        CoordinateSequence cseq = lineString.getCoordinateSequence();
        int size = cseq.size();
        if (size <= 1)
            return 0;

        int shiftX = 0;//invariant: == shiftXPage*360
        int shiftXPage = 0;
        int shiftXPageMin = 0/* <= 0 */, shiftXPageMax = 0; /* >= 0 */
        double prevX = cseq.getX(0);
        for(int i = 1; i < size; i++) {
            double thisX_orig = cseq.getX(i);
            assert thisX_orig >= -180 && thisX_orig <= 180 : "X not in geo bounds";
            double thisX = thisX_orig + shiftX;
            if (prevX - thisX > 180) {//cross dateline from left to right
                thisX += 360;
                shiftX += 360;
                shiftXPage += 1;
                shiftXPageMax = Math.max(shiftXPageMax,shiftXPage);
            } else if (thisX - prevX > 180) {//cross dateline from right to left
                thisX -= 360;
                shiftX -= 360;
                shiftXPage -= 1;
                shiftXPageMin = Math.min(shiftXPageMin,shiftXPage);
            }
            if (shiftXPage != 0)
                cseq.setOrdinate(i,0,thisX);
        }
        if (lineString instanceof LinearRing) {
            assert cseq.getCoordinate(0).equals(cseq.getCoordinate(size-1));
            assert shiftXPage == 0;//starts and ends at 0
        }
        assert shiftXPageMax >= 0 && shiftXPageMin <= 0;
        //Unfortunately we are shifting again; it'd be nice to be smarter and shift once
        shiftGeomByX(lineString, shiftXPageMin * -360);
        int crossings = shiftXPageMax - shiftXPageMin;
        if (crossings > 0)
            lineString.geometryChanged();
        return crossings;
    }

    private static void shiftGeomByX(Geometry geom, final int xShift) {
        if (xShift == 0)
            return;
        geom.apply(new CoordinateSequenceFilter() {
            @Override
            public void filter(CoordinateSequence seq, int i) {
                seq.setOrdinate(i, 0, seq.getX(i) + xShift );
            }

            @Override public boolean isDone() { return false; }

            @Override public boolean isGeometryChanged() { return true; }
        });
    }

    private static Geometry unionGeometryCollection(Geometry geom) {
        if (geom instanceof GeometryCollection) {
            return geom.union();
        }
        return geom;
    }

    /** This "pages" through standard geo boundaries offset by multiples of 360 longitudinally that intersect
     * geom, and the intersecting results of a page and the geom are shifted into the standard -180 to +180 and added
     * to a new geometry that is returned.
     */
    private static Geometry cutUnwrappedGeomInto360(Geometry geom) {
        Envelope geomEnv = geom.getEnvelopeInternal();
        if (geomEnv.getMinX() >= -180 && geomEnv.getMaxX() <= 180)
            return geom;
        assert geom.isValid() : "geom";

        //TODO support geom's that start at negative pages; will avoid need to previously shift in unwrapDateline(geom).
        List<Geometry> geomList = new ArrayList<Geometry>();
        //page 0 is the standard -180 to 180 range
        for(int page = 0; true; page++) {
            double minX = -180 + page*360;
            if (geomEnv.getMaxX() <= minX)
                break;
            Geometry rect = geom.getFactory().toGeometry(new Envelope(minX,minX+360,-90,90));
            assert rect.isValid() : "rect";
            Geometry pageGeom = rect.intersection(geom);//JTS is doing some hard work
            assert pageGeom.isValid() : "pageGeom";

            shiftGeomByX(pageGeom,page*-360);
            geomList.add(pageGeom);
        }
        return UnaryUnionOp.union(geomList);
    }

//  private static Geometry removePolyHoles(Geometry geom) {
//    //TODO this does a deep copy of geom even if no changes needed; be smarter
//    GeometryTransformer gTrans = new GeometryTransformer() {
//      @Override
//      protected Geometry transformPolygon(Polygon geom, Geometry parent) {
//        if (geom.getNumInteriorRing() == 0)
//          return geom;
//        return factory.createPolygon((LinearRing) geom.getExteriorRing(),null);
//      }
//    };
//    return gTrans.transform(geom);
//  }
//
//  private static Geometry snapAndClean(Geometry geom) {
//    return new GeometrySnapper(geom).snapToSelf(GeometrySnapper.computeOverlaySnapTolerance(geom), true);
//  }
}
