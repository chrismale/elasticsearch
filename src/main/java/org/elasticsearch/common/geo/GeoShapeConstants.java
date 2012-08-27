package org.elasticsearch.common.geo;

import com.spatial4j.core.context.jts.JtsSpatialContext;
import com.spatial4j.core.distance.DistanceUnits;
import com.vividsolutions.jts.geom.GeometryFactory;

public interface GeoShapeConstants {

    public final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    // TODO: Unsure if the units actually matter since we dont do distance calculations
    public final JtsSpatialContext SPATIAL_CONTEXT = new JtsSpatialContext(DistanceUnits.KILOMETERS);
}
