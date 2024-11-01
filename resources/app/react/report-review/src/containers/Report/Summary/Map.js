import React from 'react';
import { useContext } from 'react';
import { YMaps, Map, TypeSelector, GeolocationControl, Placemark } from 'react-yandex-maps';
import ReportContext from '../../../context/ReportContext';
const ReportMap = () => {
  const { report } = useContext(ReportContext);
  const coordinates = [+report.lat, +report.lng];
  return (
    <div>
      <YMaps>
        <Map
          width={'100%'}
          height={'80vh'}
          defaultState={{
            center: coordinates,
            zoom: 14,
          }}>
          <TypeSelector options={{ float: 'right' }} />
          <GeolocationControl options={{ float: 'left' }} />
          <Placemark geometry={coordinates} />
        </Map>
      </YMaps>
    </div>
  );
}

export default ReportMap;
