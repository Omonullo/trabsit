import * as R from "ramda";
import React, { useMemo, useRef, useState, useCallback } from "react";
import VideoSection from "../containers/Report/VideoSection";
import Summary from "../containers/Report/Summary/Summary";
import { HotKeys } from "react-hotkeys";
import S from "../style";
import { useSelector } from "react-redux";
import Offenses from "../containers/Report/Offenses";
import ReportContext from "../context/ReportContext";
import { useFetchAsync, handleUrl } from "../utils";
import { Result, Button } from "antd";
import { useEffect } from "react";
import Swal from "sweetalert2";
import t from "../lang";
import { nanoid } from "nanoid";
import ReportConfirmation from "../containers/Report/ReportConfirmation";
import SimpleReactLightbox from "simple-react-lightbox";
import moment from "moment";

const DEFAULT_REPORT_DATA = {
  articles: [],
  areas: [],
  report: {},
  responses: [],
};

export default function Review() {
  const splitUrl = window.location.pathname.split("/");
  const isReviewPage = window.location.pathname
    .split("/")
    .slice(-1)[0]
    .endsWith("review");
  const reportId = splitUrl[splitUrl.length - 2];
  const reportDataUrl = useMemo(
    () => ({ url: `/staff/reports/${reportId}/review?force=true` }),
    [reportId]
  );
  const { error, data: newReportData } = useFetchAsync(reportDataUrl);
  const [{ articles, areas, report, responses }, setReportData] = useState(
    DEFAULT_REPORT_DATA
  );
  const isReportReviewed = !!(error && error.includes("400"));
  const [indexedOffenses, setIndexedOffenses] = useState({});
  const videoRef = useRef(null);
  const extraVideoRef = useRef(null);
  const [tab, setTab] = useState("video");

  const detectionParams = useMemo(() => ({ url: report?.video_detections }), [
    report?.video_detections,
  ]);
  const extraDetectionParams = useMemo(
    () => ({ url: report?.extra_video_detections }),
    [report?.extra_video_detections]
  );
  const { data: videoDetectionsData } = useFetchAsync(detectionParams);
  const { data: extraVideoDetectionsData } = useFetchAsync(
    extraDetectionParams
  );

  const {
    imageType,
    ready: croppedImageReady,
    id,
    vehicle_id_img,
    vehicle_img,
  } = useSelector((state) => state.offenseImage || {});

  useEffect(() => {
    setReportData((state) => ({ ...state, ...newReportData }));
  }, [newReportData]);

  useEffect(() => {
    setIndexedOffenses(
      report.offenses
        ? R.pipe(
            R.reject(R.isNil),
            R.map((offense) =>
              R.assoc("extraInfo", !!offense.extra_response, offense)
            ),
            R.indexBy(R.prop("id"))
          )(report.offenses)
        : {}
    );
  }, [report.offenses]);

  const addOffense = useCallback((offense) => {
    const id = offense.id ? offense.id : nanoid(12);
    const removable = !offense.citizen_id;
    setIndexedOffenses(
      R.assoc(id, {
        ...offense,
        id,
        removable,
      })
    );
    return id;
  }, []);

  const updateOffense = useCallback((offenseId, offense) => {
    setIndexedOffenses((offensesById) => ({
      ...offensesById,
      [offenseId]: { ...offensesById[offenseId], ...offense },
    }));
  }, []);

  useEffect(() => {
    if (croppedImageReady) {
      updateOffense(id, {
        [imageType]:
          imageType === "vehicle_id_img" ? vehicle_id_img : vehicle_img,
      });
    }
  }, [croppedImageReady, imageType, vehicle_id_img, vehicle_img]);

  useEffect(() => {
    document.title =
      "Yollarimizni birga hafvisz qilyalik http://video.yhxbb.uz";
  }, []);

  const updateReport = useCallback((report) => {
    setReportData((state) => ({
      ...state,
      report: { ...state.report, ...report },
    }));
  }, []);

  const applyRejectionToAll = useCallback(
    ({ response_id, description, extraInfo, extra_response }) => {
      setIndexedOffenses((indexedOffenses) =>
        R.pipe(
          R.values, // array of offenses
          R.filter(
            (offense) =>
              !offense.accept_time &&
              (!offense.reject_time ||
                (offense.reject_time &&
                  moment().diff(moment(offense.reject_time), "days") < 30)) &&
              !offense.removable
          ),
          R.map(
            R.mergeLeft({
              // map through offenses and assoc response_id ...
              response_id,
              description,
              extraInfo,
              extra_response,
              status: "rejected",
            })
          ),
          R.indexBy(R.prop("id")),
          R.mergeRight(indexedOffenses)
        )(indexedOffenses)
      );
    },
    []
  );
  const removeOffense = useCallback((offenseId) => {
    Swal.fire({
      text: t("Transport vositasini o'chirib tashlashga ishonchingiz komilmi?"),
      confirmButtonText: t("Ha"),
      cancelButtonText: t("Yo'q"),
      showCloseButton: true,
      showCancelButton: true,
    }).then(({ value }) => {
      if (value) {
        setIndexedOffenses(R.dissoc(offenseId));
      }
    });
  }, []);
  const contextValues = useMemo(
    () => ({
      areas,
      articles,
      report,
      responses,
      addOffense,
      removeOffense,
      updateOffense,
      indexedOffenses,
      updateReport,
      isReviewPage,
      applyRejectionToAll,
      videoDetectionsData,
      extraVideoDetectionsData,
      videoRef,
      extraVideoRef,
      tab,
      setTab,
    }),
    [
      areas,
      articles,
      report,
      responses,
      addOffense,
      removeOffense,
      updateOffense,
      indexedOffenses,
      updateReport,
      isReviewPage,
      applyRejectionToAll,
      videoDetectionsData,
      extraVideoDetectionsData,
      videoRef,
      extraVideoRef,
      tab,
      setTab,
    ]
  );

  if (isReportReviewed) {
    return (
      <Result
        status="404"
        title="404"
        subTitle="Sorry, the page you visited does not exist."
        extra={
          <Button type="primary" onClick={() => handleUrl("/")}>
            Back Home
          </Button>
        }
      />
    );
  }
  return (
    <ReportContext.Provider value={contextValues}>
      <HotKeys
        keyMap={{
          playPause: "backspace",
          fastForward: "right",
          backward: "left",
        }}
      >
        <S.Wrapper>
          <S.Container>
            <div className="kt-subheader kt-grid__item" id="kt_subheader">
              <div className="kt-subheader__main">
                <h3 className="kt-subheader__title">
                  {t("Video yozuv")} №{report.number}
                </h3>
                <h4 className="kt-subheader__desc">
                  {" "}
                  {isReviewPage ? null : t("Video yozuvni ko'rib chiqish")}
                </h4>
              </div>
            </div>
            <VideoSection />
            <div className="divider" />
            <Summary />
            <div className="divider" />
            <SimpleReactLightbox>
              <Offenses />
            </SimpleReactLightbox>

            {/* <div className="text-right"> */}
          </S.Container>

          <ReportConfirmation
            indexedOffenses={indexedOffenses}
            reportId={reportId}
            report={report}
            isReviewPage={isReviewPage}
          />
        </S.Wrapper>
      </HotKeys>
    </ReportContext.Provider>
  );
}
