/** The mobile API transports WGS84 GPS points, longitude first. */
export function parseGpsPoint(wkt: unknown): [number, number] | null {
  if (typeof wkt !== 'string') return null;
  const match = /^\s*(?:SRID=4326;)?POINT\s*\(\s*([^\s]+)\s+([^\s]+)\s*\)\s*$/i.exec(wkt);
  if (!match) return null;
  const lng = Number(match[1]), lat = Number(match[2]);
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || Math.abs(lng) > 180 || Math.abs(lat) > 90 || (lng === 0 && lat === 0)) return null;
  return [lng, lat];
}

// Gaode imagery uses GCJ02 in mainland China. Convert at the display boundary only.
export function gpsToGaode(lng: number, lat: number): [number, number] {
  if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) return [lng, lat];
  const x = lng - 105, y = lat - 35, pi = Math.PI;
  let dLat = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*Math.sqrt(Math.abs(x));
  dLat += (20*Math.sin(6*x*pi) + 20*Math.sin(2*x*pi))*2/3;
  dLat += (20*Math.sin(y*pi) + 40*Math.sin(y/3*pi))*2/3;
  dLat += (160*Math.sin(y/12*pi) + 320*Math.sin(y*pi/30))*2/3;
  let dLng = 300 + x + 2*y + .1*x*x + .1*x*y + .1*Math.sqrt(Math.abs(x));
  dLng += (20*Math.sin(6*x*pi) + 20*Math.sin(2*x*pi))*2/3;
  dLng += (20*Math.sin(x*pi) + 40*Math.sin(x/3*pi))*2/3;
  dLng += (150*Math.sin(x/12*pi) + 300*Math.sin(x/30*pi))*2/3;
  const rad = lat/180*pi, a = 6378245, eccentricity = .00669342162296594323;
  const magic = 1 - eccentricity*Math.sin(rad)**2, root = Math.sqrt(magic);
  dLat = dLat*180/((a*(1-eccentricity))/(magic*root)*pi);
  dLng = dLng*180/(a/root*Math.cos(rad)*pi);
  return [lng + dLng, lat + dLat];
}

/** Map clicks on Gaode tiles must be converted back before writing the GPS contract. */
export function gaodeToGps(lng: number, lat: number): [number, number] {
  let result: [number, number] = [lng, lat];
  for (let i = 0; i < 6; i++) {
    const display = gpsToGaode(...result);
    result = [result[0] - (display[0] - lng), result[1] - (display[1] - lat)];
  }
  return result;
}
