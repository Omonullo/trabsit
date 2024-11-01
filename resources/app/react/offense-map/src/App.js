import * as R from 'ramda';
import {
  GoogleMap,
  LoadScript,
  HeatmapLayer,
  Marker,
  MarkerClusterer
} from "@react-google-maps/api";
import {useMemo, useEffect, useRef, useState, useCallback} from "react";
import {Button, Checkbox, Select, Space} from "antd";
import moment from 'moment'
import {DatePicker} from 'antd';
import {getArticles, getPoints} from "./service";
import {
  useQueryParam,
  StringParam,
  withDefault,
} from 'use-query-params';
import {SyncOutlined} from "@ant-design/icons";
import {getLngLng, latLng2Str} from "./utils";

const {RangePicker} = DatePicker;

function IdentityComponent(props) {
  return props?.children;
}

const googleLibraries = ["visualization"];

function rangeToString([from, to]) {
  return `${from.format('DD.MM.YYYY')}-${to.format('DD.MM.YYYY')}`
}

const Loader = window.google ? IdentityComponent : LoadScript;

const defaultRange = rangeToString(
  [
    moment().subtract(1, 'month'),
    moment()
  ]
);

const stringToRange = (str) => {
  const [from, to] = str.split('-');
  return [moment(from, 'DD.MM.YYYY'), moment(to, 'DD.MM.YYYY')]
}

const t = window.t || R.identity;

function App() {
  const [data, setData] = useState([]);
  const [range, setRange] = useQueryParam('range', withDefault(StringParam, defaultRange));
  const [zoom, setZoom] = useQueryParam('zoom', withDefault(StringParam, '7'));
  const [articles, setArticles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [googleMapsLoaded, setGoogleMapsLoaded] = useState(false);
  const [selectedArticleIds, setSelectedArticleIds] = useQueryParam(
    'article_ids',
    withDefault(StringParam, '39')
  );
  const [center, setCenter] = useQueryParam('center', withDefault(StringParam, '41.27232684468183,65.59293374104936'));

  useEffect(() => {
    getArticles().then(setArticles)
  }, []);
  const refreshPoints = useCallback(
    () => {
      setLoading(true)
      const incidentRange = range.split('-')
      getPoints(
        incidentRange[0],
        incidentRange[1],
        selectedArticleIds.split(',')
      ).then(points => {
        const newData = R.map(({lat, lng, incident_date, report_id, number}) => {
          return {
            location: new window.google.maps.LatLng(
              parseFloat(lat),
              parseFloat(lng)
            ),
            url: `/staff/reports/${report_id}/view#${number}`,
            id: number,
            date: moment(incident_date).format('DD.MM.yyyy HH:mm')
          };
        })(points);
        setData(newData);
      }).finally(() => {
        setLoading(false);
      });
    },
    [range, selectedArticleIds],
  )

  useEffect(() => {
    if (googleMapsLoaded) {
      refreshPoints();
    }
  }, [googleMapsLoaded]);


  const [showHeat, setShowHeat] = useState(true);
  const [showPoints, setShowPoints] = useState(true);
  const mapRef = useRef(null);
  const map = mapRef.current;
  const pointsCluster = useMemo(() => (
    !loading && showPoints && data.length > 0 ? (
      <MarkerClusterer
        maxZoom={18}
        averageCenter={true}
        onClick={(cluster)=>{
          setCenter(latLng2Str(cluster.getCenter()))
        }}
      >
        {(clusterer) =>
          data.map(({location, url, date, id}) => (
            <Marker
              title={date}
              key={id}
              onDblClick={() => {
                setData(data.filter((point) => point.url !== url))
                window.open(url, '_blank');
              }}
              position={location}
              clusterer={clusterer}/>))}
      </MarkerClusterer>
    ) : null
  ), [showPoints, data, loading]);
  const [fromM, toM] = stringToRange(range);
  const days = toM.diff(fromM, 'days');
  const [lat, lng] = center.split(',').map(parseFloat);
  return (
    <div style={{position: 'relative', height: '100%', width: '100%'}}>
      <Loader
        onLoad={() => setGoogleMapsLoaded(true)}
        libraries={googleLibraries}
        googleMapsApiKey={window.REACT_APP_GOOGLE_KEY || process.env.REACT_APP_GOOGLE_KEY}>
        <GoogleMap
          options={{
            draggable: !loading,
            streetViewControl: false,
          }}
          onLoad={(m) => (mapRef.current = m)}
          ref={mapRef}
          mapContainerStyle={{
            width: "100%",
            height: "100%",
            overflow: 'hidden',
          }}
          onDragEnd={() => {
            if (map) {
              setCenter(latLng2Str(map.getCenter()));
            }
          }}
          onZoomChanged={() => {
            if (map) {
              setZoom(map?.zoom);
              // setCenter(latLng2Str(map.getCenter()));
            }
          }}
          center={{lat, lng}}
          zoom={parseInt(zoom)}>
          {showHeat && data.length > 0 ?
            <HeatmapLayer
              options={{
                gradient: [
                  "rgba(102, 255, 0, 0)",
                  "rgb(210,255,0)",
                  "rgba(244, 227, 0, 1)",
                  "rgba(249, 198, 0, 1)",
                  "rgba(255, 170, 0, 1)",
                  "rgba(255, 113, 0, 1)",
                  "rgba(255, 57, 0, 1)",
                  "rgba(255, 0, 0, 1)"],
                dissipating: true,
                opacity: 0.5,
                radius: 40,
                maxIntensity: days,
              }}
              data={data}/> : null}
          {pointsCluster}
        </GoogleMap>
      </Loader>
      <div style={{
        position: 'absolute',
        bottom: '2rem',
        left: 10
      }}>
        <Space direction="vertical" size={12}>
          <Checkbox
            checked={showHeat}
            onChange={(e) => setShowHeat(e.target.checked)}>{t("Показывать температуру")}</Checkbox>
          <Checkbox
            checked={showPoints}
            onChange={(e) => setShowPoints(e.target.checked)}>{t("Показывать точки")}</Checkbox>
          <RangePicker value={range.split('-').map(v => moment(v, 'DD.MM.YYYY'))}
                       allowClear={false}
                       onChange={(v) => setRange(rangeToString(v))}
                       format={['DD.MM.YYYY', 'DD.MM.YYYY']}/>
          <Select
            filterOption={(input, option) =>
              option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0 ||
              option.text.toLowerCase().indexOf(input.toLowerCase())
            }
            filterSort={(optionA, optionB) =>
              optionA.children.toLowerCase().localeCompare(optionB.children.toLowerCase())
            }
            onChange={(v) => {
              setSelectedArticleIds(v.join(','))
            }}
            style={{minWidth: 250}}
            mode="multiple"
            allowClear
            placeholder={t("Выберите статью")}
            value={selectedArticleIds.split(',').filter(R.compose(R.not, R.isEmpty))}
          >
            {articles?.map(article => (
              <Select.Option text={article.text} key={article.id}>{article.alias}</Select.Option>
            ))}
          </Select>
          <Button loading={loading} type="primary" icon={<SyncOutlined/>} onClick={refreshPoints}>
            {t("Обновить")}
          </Button>
        </Space>
      </div>
    </div>

  );
}

export default App;
