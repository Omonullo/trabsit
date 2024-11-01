import { createContext } from 'react';

export default createContext({
  articles: [],
  areas: [],
  responses: [],
  report: {
    lat: 0,
    lng: 0
  }
});