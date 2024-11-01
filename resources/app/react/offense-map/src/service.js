import axios from "axios";
import * as R from "ramda";

const getDirectionsService = R.memoizeWith(R.identity, (n) => {
  const GoogleMaps = window.google?.maps;
  return new GoogleMaps.DirectionsService();
});

export const getPoints = async (startDate, endDate, article_ids) => {
    const options = {
      method: 'GET',
      url: '/api/staff/offense-points',
      params: {
        date_range: `${startDate}-${endDate}`,
        article_ids: article_ids?.join(',')
      },
    };
    const {data} = await axios.request(options)
    return data?.points;
  }
;

export const getArticles = async () => {
    const options = {
      method: 'GET',
      url: '/api/staff/articles',
    };
    const {data} = await axios.request(options)
    return data;
  }
;
