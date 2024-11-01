export function getLngLng(center) {
  const { lat, lng } = center;
  return { lat: lat(), lng: lng() };
}

export function latLng2Str(center) {
  const { lat, lng } = center;
  return `${lat()},${lng()}`;
}
