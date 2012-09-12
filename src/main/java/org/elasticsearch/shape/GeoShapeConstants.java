package org.elasticsearch.shape;

import com.spatial4j.core.context.jts.JtsSpatialContext;
import com.spatial4j.core.distance.DistanceUnits;
import com.vividsolutions.jts.geom.GeometryFactory;

public interface GeoShapeConstants {

    public final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    // TODO: Unsure if the units actually matter since we dont do distance calculations
    public final JtsSpatialContext SPATIAL_CONTEXT = new JtsSpatialContext(DistanceUnits.KILOMETERS);

    public static ShapeDataSet NATURAL_EARTH_DATA_COUNTRIES = new RemoteESRIShapeDataSet("natural_earth_data_cities",
            "http://www.naturalearthdata.com/http//www.naturalearthdata.com/download/110m/cultural/110m-admin-0-countries.zip",
            "name");
}
