import React from "react";
import ReactDOM from "react-dom";
import Review from "./pages/Review";
import "antd/dist/antd.css";
import { Provider } from "react-redux";
import store from "./store";
import S from "./style";

ReactDOM.render(
  <Provider store={store}>
    <S.GlobalStyle />
    <Review />
  </Provider>,
  document.getElementById("root")
);
