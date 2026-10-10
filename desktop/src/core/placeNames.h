#pragma once

#include <QHash>
#include <QString>
#include <QVector>

// Names for where a photo was taken. The phone asks its system geocoder; a desktop has none, so the names come from a GeoNames city list kept on this machine
// (~/.local/share/vados/gallery/cities1000.txt, or cities500, cities5000, cities15000), read once. Without one, a place is named by its coordinates.
class PlaceNames {
public:
    struct Place {
        QString key;
        QString city;
        QString country;
    };

    static PlaceNames &shared();
    bool hasList();
    // The nearest city within reach of a point; without a list (or nothing near), the ~11 km cell the point lies in, named by its coordinates.
    Place placeOf(double latitude, double longitude);

private:
    struct City {
        double latitude;
        double longitude;
        QString name;
        QString country;
        QString id;
    };
    void load();

    bool m_isLoaded = false;
    // Cities by the whole degree cell they lie in, so a lookup only looks at the cells around a point.
    QHash<qint64, QVector<City>> m_cells;
};
