import React from "react";
import ReactDOM from "react-dom";
import App from "./App";
import reportWebVitals from "./reportWebVitals";
import {QueryParamProvider} from 'use-query-params';
import {BrowserRouter as Router, Route} from 'react-router-dom';
import "antd/dist/antd.css";
import './index.css'

ReactDOM.render(
  <React.StrictMode>
    <Router>
      <QueryParamProvider ReactRouterRoute={Route}>
        <App/>
      </QueryParamProvider>
    </Router>
  </React.StrictMode>,
  document.querySelector("#offense-map")
)

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
