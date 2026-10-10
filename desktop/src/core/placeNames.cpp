#include "placeNames.h"

#include <QFile>
#include <QLocale>
#include <QStandardPaths>

#include <cmath>

namespace {

// Farther than this from every listed city, a point is named by its coordinates.
constexpr double REACH_KM = 30.0;

qint64 cellKey(int latitude, int longitude)
{
    return qint64(latitude + 90) * 1000 + (longitude + 180);
}

double distanceKm(double firstLatitude, double firstLongitude, double secondLatitude, double secondLongitude)
{
    const double toRadians = M_PI / 180.0;
    const double dLatitude = (secondLatitude - firstLatitude) * toRadians;
    const double dLongitude = (secondLongitude - firstLongitude) * toRadians;
    const double a = std::sin(dLatitude / 2) * std::sin(dLatitude / 2)
        + std::cos(firstLatitude * toRadians) * std::cos(secondLatitude * toRadians) * std::sin(dLongitude / 2) * std::sin(dLongitude / 2);
    return 6371.0 * 2 * std::atan2(std::sqrt(a), std::sqrt(1 - a));
}

QString coordinatesText(double latitude, double longitude)
{
    return QStringLiteral("%1° %2, %3° %4").arg(std::abs(latitude), 0, 'f', 1).arg(latitude >= 0 ? QLatin1Char('N') : QLatin1Char('S'))
        .arg(std::abs(longitude), 0, 'f', 1).arg(longitude >= 0 ? QLatin1Char('E') : QLatin1Char('W'));
}

} // namespace

PlaceNames &PlaceNames::shared()
{
    static PlaceNames names;
    return names;
}

bool PlaceNames::hasList()
{
    load();
    return !m_cells.isEmpty();
}

void PlaceNames::load()
{
    if (m_isLoaded)
        return;
    m_isLoaded = true;
    const QString folder = QStandardPaths::writableLocation(QStandardPaths::GenericDataLocation) + QStringLiteral("/vados/gallery/");
    for (const QString &name : {QStringLiteral("cities500.txt"), QStringLiteral("cities1000.txt"), QStringLiteral("cities5000.txt"), QStringLiteral("cities15000.txt")}) {
        QFile file(folder + name);
        if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
            continue;
        // GeoNames' columns: id, name, ascii name, alternate names, latitude, longitude, feature class, feature code, country code, …
        while (!file.atEnd()) {
            const QList<QByteArray> columns = file.readLine().split('\t');
            if (columns.size() < 9)
                continue;
            City city;
            city.id = QString::fromUtf8(columns.at(0));
            city.name = QString::fromUtf8(columns.at(1));
            city.latitude = columns.at(4).toDouble();
            city.longitude = columns.at(5).toDouble();
            city.country = QLocale::territoryToString(QLocale::codeToTerritory(QString::fromLatin1(columns.at(8))));
            m_cells[cellKey(int(std::floor(city.latitude)), int(std::floor(city.longitude)))].append(city);
        }
        return;
    }
}

PlaceNames::Place PlaceNames::placeOf(double latitude, double longitude)
{
    load();
    const City *nearest = nullptr;
    double nearestKm = REACH_KM;
    const int cellLatitude = int(std::floor(latitude));
    const int cellLongitude = int(std::floor(longitude));
    for (int dLatitude = -1; dLatitude <= 1; ++dLatitude)
        for (int dLongitude = -1; dLongitude <= 1; ++dLongitude) {
            const auto cell = m_cells.constFind(cellKey(cellLatitude + dLatitude, cellLongitude + dLongitude));
            if (cell == m_cells.cend())
                continue;
            for (const City &city : *cell) {
                const double km = distanceKm(latitude, longitude, city.latitude, city.longitude);
                if (km < nearestKm) {
                    nearestKm = km;
                    nearest = &city;
                }
            }
        }
    if (nearest)
        return {QStringLiteral("city:") + nearest->id, nearest->name, nearest->country};
    const double cellLatitudeValue = std::round(latitude * 10) / 10;
    const double cellLongitudeValue = std::round(longitude * 10) / 10;
    return {QStringLiteral("cell:%1,%2").arg(cellLatitudeValue, 0, 'f', 1).arg(cellLongitudeValue, 0, 'f', 1), coordinatesText(cellLatitudeValue, cellLongitudeValue), QString()};
}
