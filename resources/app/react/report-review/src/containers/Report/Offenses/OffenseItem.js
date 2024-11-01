import * as R from "ramda";
import React, { useState, useRef, useMemo, useContext } from "react";
import {
  Col,
  Row,
  Radio,
  Checkbox,
  Button,
  Divider,
  Tooltip,
  Alert,
} from "antd";
import { FiTrash2 } from "react-icons/fi";
import t from "../../../lang";
import StyledComponent from "../style";
import ReportContext from "../../../context/ReportContext";
import { useEffect } from "react";
import { AiOutlineVideoCamera } from "react-icons/ai";
import {
  axios,
  cancelToken,
  canSaveOffense,
  createErrors,
  lpDetectionToHumanReadableText,
} from "../../../utils";
import moment from "moment";
import ErrorMessage from "./ErrorMessage";
import OffenseStatus from "./OffenseStatus";
import ImageViewer from "./ImageViewer";
import PlateNumberMask from "./PlateNumberMask";
// const baseURL = process.env.NODE_ENV !== 'development' ? 'https://jarima.kash.uz' : '';

function toDataUrl(url, callback) {
  const xhr = new XMLHttpRequest();
  xhr.onload = function () {
    const reader = new FileReader();
    reader.onloadend = function () {
      callback(reader.result);
    };
    reader.readAsDataURL(xhr.response);
  };
  xhr.open("GET", url);
  xhr.responseType = "blob";
  xhr.send();
}
export default function OffenseItem({ offense }) {
  const { id } = offense || {};
  const isSubmittedByCitizen = !!offense.citizen_id;
  const [articleVisible, setArticleVisible] = useState(false);
  const [responseVisible, setResponseVisible] = useState(false);
  const [imageVisible, setImageVisible] = useState(false);
  const reportContext = useContext(ReportContext);

  const parentRef = useRef();
  const [activeImageIndex, setActiveImageIndex] = useState(0);

  const videoFps = reportContext.videoDetectionsData?.fps || 30;
  const extraVideoFps = reportContext.extraVideoDetectionsData?.fps || 30;
  const videoPlateTimestamps =
    reportContext.videoDetectionsData?.grouped_by_text?.[offense.vehicle_id];
  const extraVideoPlateTimestamps =
    reportContext.extraVideoDetectionsData?.grouped_by_text?.[
      offense.vehicle_id
    ];
  useEffect(() => {
    if (offense.status === "failed") {
      reportContext.updateOffense(id, {
        status: "accepted",
        originalStatus: offense.status,
      });
      if (offense.vehicle_id_img) {
        toDataUrl(offense.vehicle_id_img, (vehicle_id_img) => {
          reportContext.updateOffense(id, { vehicle_id_img });
        });
      }
      if (offense.vehicle_img) {
        toDataUrl(offense.vehicle_img, (vehicle_img) => {
          reportContext.updateOffense(id, { vehicle_img });
        });
      }
    }
  }, [offense.status, id]);

  const filteredPlateTimestamps = useMemo(() => {
    const timestamps =
      videoPlateTimestamps?.reduce((acc, item, index) => {
        const nextFrameId = videoPlateTimestamps[index + 1]?.frame_id ?? 0;
        // if the amount of time gretaer than half seconds then add frame_id to list
        if (Math.abs(nextFrameId - item.frame_id) > videoFps * 0.1) {
          return [...acc, item.frame_id];
        }
        return acc;
      }, []) || [];

    if (timestamps.length >= 3) {
      return [
        R.head(timestamps),
        timestamps[Math.round(timestamps.length / 2) - 1],
        R.last(timestamps),
      ];
    } else {
      return timestamps;
    }
  }, [videoPlateTimestamps, videoFps]);

  const filteredExtraPlateTimestamps = useMemo(() => {
    const timestamps =
      extraVideoPlateTimestamps?.reduce((acc, item, index) => {
        const nextFrameId = extraVideoPlateTimestamps[index + 1]?.frame_id ?? 0;
        // if the amount of time
        if (Math.abs(nextFrameId - item.frame_id) > extraVideoFps * 0.1) {
          return [...acc, item.frame_id];
        }
        return acc;
      }, []) || [];

    if (timestamps.length >= 3) {
      return [
        R.head(timestamps),
        timestamps[Math.round(timestamps.length / 2) - 1],
        R.last(timestamps),
      ];
    } else {
      return timestamps;
    }
  }, [extraVideoPlateTimestamps, extraVideoFps]);
  useEffect(() => {
    const source = cancelToken();

    const fetchVehicleOtherOffences = async () => {
      try {
        const { data } = await axios(
          `/staff/reports/vehicle?vehicle_id=${
            offense.vehicle_id
          }&exclude_report_id=${reportContext.report.id}&incident_time=${moment(
            reportContext.report.incident_time
          ).format("DD.MM.YYYY-DD.MM.YYYY")}`,
          {
            cancelToken: source.token,
            baseURL: "/",
          }
        );
        if (data.count) {
          reportContext.updateOffense(id, { otherOffences: data.count });
        }
      } catch (err) {
        console.log(err);
      }
    };
    fetchVehicleOtherOffences();
    return () => source.cancel("Canceled fetch other offences");
  }, [offense.vehicle_id]);

  const handleInput = (e) => {
    const { name, value } = e.target;
    reportContext.updateOffense(id, { [name]: value });
  };

  const handleCheck = (e) => {
    const { name, checked } = e.target;
    reportContext.updateOffense(id, { [name]: checked });
  };

  const handleSelect = (name, value) => {
    if (name === "response_id") {
      setResponseVisible(false);
    } else if (name === "article_id") {
      setArticleVisible(false);
    }
    reportContext.updateOffense(id, { [name]: value });
  };

  const handleStatus = (e) => {
    const { value } = e.target;
    let data = { status: value, article_id: null };
    if (value === "accepted") {
      data = { ...data, response_id: null };
      // setArticleVisible(true);
    } else {
      // setResponseVisible(true);
    }
    reportContext.updateOffense(id, data);
  };

  const subTitle = offense.removable
    ? t("Yangi qoidabuzalik")
    : t("Qoidabuzarlik") + `-${offense.number}`;
  const canStatusBeChanged = canSaveOffense(offense);

  const errors = useMemo(() => {
    const duplicateErrors =
      R.pipe(
        R.filter(
          (otherOffenses) => offense.vehicle_id === otherOffenses.vehicle_id
        ),
        R.values
      )(reportContext.indexedOffenses).length > 1 &&
      offense.status === "accepted"
        ? {
            vehicle_id: {
              name: "vehicle_id",
              filed: "License Plate",
              value: "This Plate Number is duplicated",
            },
          }
        : {};

    return R.mergeRight(
      R.indexBy(R.prop("name"), createErrors(offense)),
      duplicateErrors
    );
  }, [offense, reportContext.indexedOffenses]);

  // TODO understand what's going on
  const foundVehicleByPlateNumber = useMemo(() => {
    return lpDetectionToHumanReadableText(
      filteredPlateTimestamps,
      0,
      offense.vehicle_id
    );
  }, [filteredPlateTimestamps, offense.vehicle_id]);

  const foundExtraVehicleByPlateNumber = useMemo(() => {
    const frame_id = filteredPlateTimestamps[0];
    return lpDetectionToHumanReadableText(
      filteredExtraPlateTimestamps,
      0,
      offense.vehicle_id
    );
  }, [filteredExtraPlateTimestamps, offense.vehicle_id]);

  const onTimestampSelect = (tab, frame_id) => {
    const currentRef =
      tab === "video" ? reportContext.videoRef : reportContext.extraVideoRef;
    const currentFps = tab === "video" ? videoFps : extraVideoFps;
    if (currentRef.current) {
      reportContext.setTab(tab);

      currentRef?.current.scrollIntoView({
        block: "center",
        behavior: "smooth",
      });

      currentRef.current.currentTime = frame_id / currentFps - 0.1;
      currentRef.current.dataset.expectedFrame = JSON.stringify(frame_id);
      currentRef.current.dataset.expectationSatisfied = JSON.stringify(false);
      currentRef.current.addEventListener(
        "loadeddata",
        () => {
          currentRef.current.currentTime = frame_id / currentFps;
        },
        { once: true }
      );
    }
  };

  return (
    <StyledComponent
      id={`offense_${offense.id}`}
      className={`offense-item ${offense.status ? offense.status : ""}`}
      ref={parentRef}
    >
      {offense.reject_time ||
      offense.accept_time ||
      offense.status === "created" ? (
        <div
          className={`status offense-item__status ${
            offense.originalStatus || offense.status
          } ${offense.removable ? "none" : ""}`}
        >
          {t(offense.originalStatus || offense.status)}
        </div>
      ) : null}

      <div className="remove-btn">
        <h3 className="sub-title">{subTitle}</h3>
        {offense.removable ? (
          <button
            onClick={() => {
              reportContext.removeOffense(id);
            }}
          >
            <FiTrash2 />
          </button>
        ) : null}
      </div>

      <Row gutter={12}>
        <Col className="label d-flex align-items-center" sm={6}>
          <label htmlFor="playback-times">{t("Указан граджданином?")}</label>
        </Col>
        <Col className="d-flex align-items-center" sm={16}>
          <Alert
            showIcon
            className="alert-msg"
            type={offense.testimony ? "success" : "info"}
            style={{ width: "max-content" }}
            message={<div>{offense.testimony ? t("Ha") : t("Yo'q")}</div>}
          />
        </Col>

        <Divider style={{ borderTop: "none", margin: 0, marginTop: "1rem" }} />

        <Col className="label d-flex align-items-center" sm={6}>
          <label htmlFor="playback-times">{t("Birinchi video")}</label>
        </Col>
        <Col sm={18}>
          {!filteredPlateTimestamps?.length &&
          {
            /*!frame_id*/
          } ? (
            <Alert
              type="error"
              showIcon
              message={t("Ushbu davalat raqami videoda avtomatik topilmadi")}
            />
          ) : null}
          {filteredPlateTimestamps?.map((frame_id) => {
            return (
              <Tooltip
                title={t("Videoda topilgan vaqti")}
                key={`video_tooltip_${frame_id}}`}
              >
                <Button
                  key={frame_id}
                  className="video-apperance"
                  icon={<AiOutlineVideoCamera />}
                  onClick={() => onTimestampSelect("video", frame_id)}
                >
                  {`${moment
                    .utc(Math.round((frame_id / videoFps) * 1000))
                    .format("mm:ss")}`}
                </Button>
              </Tooltip>
            );
          })}
          {filteredPlateTimestamps.length &&
          {
            /*!frame_id*/
          } ? (
            <span>{foundVehicleByPlateNumber}</span>
          ) : null}
        </Col>
        {reportContext.report.extra_video && (
          <>
            <Divider
              style={{ borderTop: "none", margin: 0, marginTop: "1rem" }}
            />

            <Col className="label d-flex align-items-center" sm={6}>
              <label htmlFor="playback-times">{t("Ikkinchi video")}</label>
            </Col>
            <Col sm={18}>
              {!filteredExtraPlateTimestamps?.length &&
              {
                /*!frame_id*/
              } ? (
                <Alert
                  type="error"
                  showIcon
                  message={t(
                    "Ushbu davalat raqami videoda avtomatik topilmadi"
                  )}
                />
              ) : null}
              {filteredExtraPlateTimestamps?.map((frame_id) => {
                return (
                  <Tooltip
                    title={t("Videoda topilgan vaqti")}
                    key={`extra_video_tooltip_${frame_id}}`}
                  >
                    <Button
                      key={frame_id}
                      className="video-apperance extra"
                      icon={<AiOutlineVideoCamera />}
                      onClick={() => onTimestampSelect("extra_video", frame_id)}
                    >
                      {`${moment
                        .utc(Math.round((frame_id / extraVideoFps) * 1000))
                        .format("mm:ss")}`}
                    </Button>
                  </Tooltip>
                );
              })}
              {filteredExtraPlateTimestamps.length &&
              {
                /*!frame_id*/
              } ? (
                <span>{foundExtraVehicleByPlateNumber}</span>
              ) : null}
            </Col>
          </>
        )}
        <Divider />

        <Col className="label d-flex align-items-center" sm={6}>
          <label htmlFor="platNumber">{t("Davlat raqami")}</label>
        </Col>
        <Col sm={18}>
          <div className="d-flex">
            <PlateNumberMask
              disabled={isSubmittedByCitizen}
              plateNumber={offense.vehicle_id}
              setPlateNumber={(value) => {
                reportContext.updateOffense(id, {
                  vehicle_id: value,
                });
              }}
            />
            <ErrorMessage errors={errors} fieldName="vehicle_id" />
            {offense.otherOffences ? (
              <a
                rel="noreferrer"
                target="_blank"
                className="text-red"
                href={`/staff/offenses?vehicle_id=${
                  offense.vehicle_id
                }&exclude_report_id=${
                  reportContext.report?.id
                }&incident_time=${moment(
                  reportContext.report.incident_time
                ).format("DD.MM.YYYY-DD.MM.YYYY")}`}
              >
                {t("Ushbu transportning boshqa qoidabuzarliklari")} (
                {offense.otherOffences})
              </a>
            ) : null}
          </div>
          {offense.otherOffences ? (
            <div className="mt-2 mb-4">
              <Checkbox
                onChange={(e) =>
                  reportContext.updateOffense(id, {
                    not_duplicate: e.target.checked,
                  })
                }
              >
                {t("Ushbu qoidabuzarlik dublikat emas")}
              </Checkbox>
            </div>
          ) : null}
        </Col>
        {offense.removable ? null : (
          <>
            <Col className="label mt-3" sm={6}>
              <label>{t("Fuqaro Ta'rifi ")}</label>
            </Col>
            <Col sm={18} className="col-left mt-3">
              <p>{offense.testimony}</p>
            </Col>
          </>
        )}
        <Col className="label d-flex align-items-center" sm={6}>
          <label className={errors.status ? "has-error" : ""}>
            {t("Status")}
          </label>
        </Col>
        <Col sm={18} className="col-left mt-3">
          <Radio.Group
            className="status-input"
            onChange={handleStatus}
            name="status"
            value={offense.status}
            disabled={!canStatusBeChanged}
            options={[
              {
                label: t("Rad etish"),
                value: "rejected",
              },
              { label: t("Jazo qo'llash"), value: "accepted" },
            ]}
          />
          <ErrorMessage errors={errors} fieldName={"status"} />
        </Col>
        <OffenseStatus
          handleCheck={handleCheck}
          handleInput={handleInput}
          handleSelect={handleSelect}
          values={offense}
          status={offense.status}
          vehicle_img={offense.vehicle_img}
          vehicle_id_img={offense.vehicle_id_img}
          // frame_id={frame_id}
          videoTimestamps={filteredPlateTimestamps}
          extraVideoTimestamps={filteredExtraPlateTimestamps}
          vehicle_id={offense.vehicle_id}
          id={id}
          articleVisible={articleVisible}
          setArticleVisible={setArticleVisible}
          responseVisible={responseVisible}
          setResponseVisible={setResponseVisible}
          setImageVisible={() => setImageVisible(true)}
          setActiveImageIndex={setActiveImageIndex}
        />
      </Row>
      <ImageViewer
        isOpen={imageVisible}
        activeImageIndex={activeImageIndex}
        images={[
          {
            src: offense.vehicle_img,
            loading: "lazy",
            alt: offense.vehicle_id,
            style: { width: "100vw", objectFit: "contain" },
          },
          {
            src: offense.vehicle_id_img,
            loading: "lazy",
            alt: offense.vehicle_id,
            style: { width: "100vw", objectFit: "contain" },
          },
        ]}
        handleClose={() => setImageVisible(false)}
      />
    </StyledComponent>
  );
}
