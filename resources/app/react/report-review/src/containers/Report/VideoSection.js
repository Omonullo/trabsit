import React, { useState, useContext } from "react";
import { Tabs } from "antd";
import StyledComponent from "./style";
import t from "../../lang";
import VideoPlayer from "./VideoPlayer";
import ReportContext from "../../context/ReportContext";

const { TabPane } = Tabs;

export default function VideoSection() {
  const { report } = useContext(ReportContext);
  return (
    <StyledComponent style={{ marginTop: -20 }}>
      <div
        className={`status player ${
          report.status === "reviewed" ? "reviewed" : ""
        }`}
      >
        {t(report.status)}
      </div>
      <VideoPlayer />
    </StyledComponent>
  );
}
