import * as R from "ramda";
import React, { useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Col,
  Divider,
  Input,
  Modal,
  Popconfirm,
  Radio,
  Row,
  Select,
  Tooltip,
} from "antd";
import {
  AiOutlineCheck,
  AiOutlineClockCircle,
  AiOutlinePlus,
  AiOutlineVideoCamera,
} from "react-icons/ai";
import ReportContext from "../../../context/ReportContext";
import t, { getLanguage } from "../../../lang";
import {
  axios,
  cancelToken,
  canSaveOffense,
  lpDetectionToHumanReadableText,
} from "../../../utils";
import { StyledOffenseModal } from "../style";
import moment from "moment";
import PlateNumberMask from "./PlateNumberMask";

const initialValue = {
  status: "",
  vehicle_id: "",
  article_number: "",
  article_id: null,
  vehicle_img: "",
  vehicle_id_img: "",
  response_id: null,
};

export default function OffenseModal({
  visible,
  hideModal,
  extraData = {
    vehicle_id: "",
    frame_id: null,
    vehicle_img: "",
    vehicle_id_img: "",
    color: "",
    model: "",
    make: "",
  },
}) {
  const reportContext = useContext(ReportContext);

  const {
    videoDetectionsData,
    extraVideoDetectionsData,
    videoRef,
    extraVideoRef,
  } = reportContext;

  const [offenseForm, setOffenseForm] = useState(initialValue);

  const vehicleId = offenseForm.vehicle_id || extraData.vehicle_id;

  const existingOffense = useMemo(
    () =>
      Object.values(reportContext.indexedOffenses).find(
        (item) => item.vehicle_id === vehicleId
      ),
    [vehicleId, reportContext.indexedOffenses]
  );

  useEffect(() => {
    if (visible) {
      setOffenseForm((state) => ({
        ...state,
        ...R.pipe(
          R.dissoc("vehicle_img"),
          R.dissoc("vehicle_id_img")
        )(existingOffense),
      }));
    }
  }, [visible, existingOffense]);

  useEffect(() => {
    if (visible) {
      setOffenseForm((state) => ({
        ...state,
        ...extraData,
      }));
    }
  }, [visible, extraData]);

  const [articleInputVisible, setArticleInputVisible] = useState(false);
  const [isConfirmationVisible, setConfirmationVisible] = useState(false);
  const [responseInputVisible, setResponseInputVisible] = useState(true);
  const canStatusBeChanged = canSaveOffense(offenseForm);
  const videoFps = videoDetectionsData?.fps || 30;
  const extraVideoFps = extraVideoDetectionsData?.fps || 30;
  const videoPlateTimestamps =
    videoDetectionsData?.grouped_by_text?.[vehicleId];
  const extraVideoPlateTimestamps =
    extraVideoDetectionsData?.grouped_by_text?.[vehicleId];

  const onTimestampSelect = (tab, frame_id) => {
    const currentRef = tab === "video" ? videoRef : extraVideoRef;
    const currentFps = tab === "video" ? videoFps : extraVideoFps;
    if (currentRef.current) {
      reportContext.setTab(tab);

      currentRef?.current.scrollIntoView({
        block: "center",
        behavior: "smooth",
      });

      currentRef.current.currentTime = frame_id / currentFps;
      currentRef.current.addEventListener(
        "loadeddata",
        () => {
          currentRef.current.currentTime = frame_id / currentFps;
        },
        { once: true }
      );
    }
  };

  const filteredPlateTimestamps = useMemo(() => {
    const timestamps =
      videoPlateTimestamps?.reduce((acc, item, index) => {
        const nextFrameId = videoPlateTimestamps[index + 1]?.frame_id ?? 0;
        // if the amount of time
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
  }, [videoPlateTimestamps]);

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
  }, [extraVideoPlateTimestamps]);
  const foundVehicleByPlateNumber = useMemo(() => {
    return lpDetectionToHumanReadableText(
      filteredPlateTimestamps,
      0,
      vehicleId
    );
  }, [filteredPlateTimestamps, vehicleId]);

  const foundExtraVehicleByPlateNumber = useMemo(() => {
    return lpDetectionToHumanReadableText(
      filteredExtraPlateTimestamps,
      0,
      vehicleId
    );
  }, [filteredExtraPlateTimestamps, vehicleId]);

  const containerRef = useRef();
  const lang = getLanguage();

  const source = cancelToken();
  const fetchOtherOffences = async () => {
    try {
      const { data } = await axios.get(
        `/staff/reports/vehicle?vehicle_id=${
          offenseForm.vehicle_id
        }&exclude_report_id=${reportContext.report?.id}&incident_time=${moment(
          reportContext.report.incident_time
        ).format("DD.MM.YYYY-DD.MM.YYYY")}`,
        { cancelToken: source.token }
      );
      setOffenseForm((state) => ({ ...state, otherOffences: data.count }));
    } catch (err) {
      console.log(err);
    }
  };

  useEffect(() => {
    fetchOtherOffences();
    return () => source.cancel("Canceling other offenses request");
  }, [offenseForm.vehicle_id]);

  const handleStatus = (e) => {
    const { value } = e.target;
    let data = { status: value, article_id: null };
    if (value === "accepted") {
      data = { ...data, response_id: null };
      // setArticleInputVisible(true);
    } else {
      // setResponseInputVisible(true);
    }
    setOffenseForm((state) => ({ ...state, ...data }));
  };

  const handleInput = (e) => {
    const { name, value } = e.target;
    setOffenseForm((state) => ({ ...state, [name]: value }));
  };

  const handleCheck = (e) => {
    const { name, checked } = e.target;
    setOffenseForm((state) => ({ ...state, [name]: checked }));
  };

  const handleSelect = (name, value) => {
    if (name === "response_id") {
      setResponseInputVisible(false);
    } else if (name === "article_id") {
      setArticleInputVisible(false);
    }
    setOffenseForm((state) => ({ ...state, [name]: value }));
  };

  const handleModal = () => {
    hideModal();
    setOffenseForm(initialValue);
  };

  const { responses, articles } = reportContext;

  const sortedResponseByPriority = useMemo(() => {
    return R.sortBy(R.descend(R.prop("priority")), responses);
  }, [responses, lang]);

  const sortedArticles = useMemo(() => {
    return reportContext.articles;
  }, [reportContext.articles, lang]);

  const articlesByID = useMemo(() => {
    let articles = {};
    reportContext.articles.forEach((item) => {
      const title =
        item[`alias_${lang}`] ||
        item.text_uz_la ||
        item.text_uz_cy ||
        item.text_ru;
      articles[item.id] = { ...item, title };
    });
    return articles;
  }, [reportContext.articles, lang]);

  const responsesByID = useMemo(() => {
    let responses = {};
    reportContext.responses.forEach((item) => {
      const title =
        item[`alias_${lang}`] ||
        item.text_uz_la ||
        item.text_uz_cy ||
        item.text_ru;
      responses[item.id] = { ...item, title };
    });
    return responses;
  }, [reportContext.responses, lang]);

  const handleSubmit = () => {
    let offenseId;
    if (existingOffense?.id) {
      const data = { ...existingOffense, ...offenseForm };
      reportContext.updateOffense(existingOffense?.id, data);
      offenseId = existingOffense?.id;
    } else {
      offenseId = reportContext.addOffense(offenseForm);
    }
    setOffenseForm(initialValue);
    hideModal();
    setTimeout(() => {
      document.getElementById(`offense_${offenseId}`)?.scrollIntoView({
        block: "center",
        behavior: "smooth",
      });
    }, 300);
  };

  const canSave =
    offenseForm.vehicle_id &&
    ["accepted", "rejected"].includes(offenseForm.status) &&
    (offenseForm.article_id || offenseForm.response_id) &&
    canSaveOffense(offenseForm);
  const ExtraInfoMessage = () => {
    return (
      <>
        <Col className="label d-flex align-items-center" sm={6}>
          <label htmlFor="playback-times">{t("Указан граджданином?")}</label>
        </Col>
        <Col className="d-flex align-items-center" sm={16}>
          <Alert
            showIcon
            className="alert-msg"
            type={offenseForm?.citizen_id ? "success" : "info"}
            style={{ width: "max-content" }}
            message={<div>{offenseForm?.citizen_id ? t("Ha") : t("Yo'q")}</div>}
          />
        </Col>

        <Divider style={{ borderTop: "none", margin: 0, marginTop: "1rem" }} />

        <Col className="label d-flex align-items-center" sm={6}>
          <label htmlFor="playback-times">{t("Birinchi video")}</label>
        </Col>
        <Col sm={16}>
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
            <Col sm={16}>
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
      </>
    );
  };
  const isSubmittedByCitizen = !!offenseForm?.citizen_id;

  const articleSelectRef = useRef(null);
  const responseSelectRef = useRef(null);
  return (
    <StyledOffenseModal className="offense-modal" ref={containerRef}>
      <Modal
        onOk={handleModal}
        visible={visible}
        onCancel={handleModal}
        title={t("Qoidabuzarlikni tanlang")}
        footer={null}
        getContainer={() => containerRef.current}
        width={"90%"}
        style={{ maxWidth: 900, paddingBottom: 40 }}
        destroyOnClose
      >
        <Row>
          <ExtraInfoMessage />
          <Divider />
          <Col span={6} className="label">
            <label htmlFor="platNumber">{t("Videodagi davalat raqami")}</label>
          </Col>
          <Col span={18} className="modal__plate_image">
            <img src={extraData.vehicle_id_img} alt="vehicle plate number" />
          </Col>

          <Col className="label" md={6}>
            <label htmlFor="platNumber">{t("Davlat raqami")}</label>
          </Col>
          <Col md={18} className="d-flex">
            <PlateNumberMask
              disabled={!canStatusBeChanged || isSubmittedByCitizen}
              plateNumber={offenseForm.vehicle_id}
              setPlateNumber={(value) => {
                setOffenseForm((state) => ({ ...state, vehicle_id: value }));
              }}
            />
            {offenseForm.otherOffences ? (
              <a
                rel="noreferrer"
                target="_blank"
                className="text-red"
                href={`/staff/offenses?vehicle_id=${vehicleId}&exclude_report_id=${
                  reportContext.report?.id
                }&incident_time=${moment(
                  reportContext.report.incident_time
                ).format("DD.MM.YYYY-DD.MM.YYYY")}`}
              >
                {t("Ushbu transportning boshqa qoidabuzarliklari")} (
                {offenseForm.otherOffences})
              </a>
            ) : null}
          </Col>
          {existingOffense?.testimony ? (
            <>
              <Col className="label mt-3" md={6}>
                <label>{t("Fuqaro Ta'rifi ")}</label>
              </Col>
              <Col md={18} className="col-left mt-3">
                <div>{existingOffense?.testimony}</div>
              </Col>
            </>
          ) : null}

          <Col className="label mt-3" md={6}>
            <label>{t("Status")}</label>
          </Col>
          <Col md={18} className="col-left mt-3">
            <Radio.Group
              disabled={!canStatusBeChanged}
              className="status-input"
              onChange={handleStatus}
              name="status"
              value={offenseForm.status}
            >
              {isSubmittedByCitizen ? (
                <Radio value={"rejected"}>{t("Rad etish")}</Radio>
              ) : null}
              <Radio value={"accepted"}>{t("Jazo qo'llash")}</Radio>
            </Radio.Group>
          </Col>
          {offenseForm.status === "rejected" ? (
            <>
              <Col className="label mt-3" md={6}>
                <label>{t("Rad etish sababi")}</label>
              </Col>
              <Col md={18} className="d-flex flex-column mt-3">
                <Select
                  disabled={!canStatusBeChanged}
                  ref={responseSelectRef}
                  onFocus={() => setResponseInputVisible(true)}
                  onBlur={() => setResponseInputVisible(false)}
                  placeholder={t("Rad etish sababini tanlang")}
                  onSelect={(val) => handleSelect("response_id", val)}
                  className="select-input"
                  value={offenseForm.response_id}
                  size="large"
                  open={responseInputVisible}
                  showSearch
                  autoFocus={responseInputVisible}
                  style={{
                    width: "100%",
                    display:
                      responseInputVisible || !offenseForm.response_id
                        ? "block"
                        : "none",
                  }}
                  filterOption={(input, option) => {
                    return (
                      option.props.label
                        .toLowerCase()
                        .indexOf(input.toLowerCase()) >= 0
                    );
                  }}
                >
                  {sortedResponseByPriority.map((item) => {
                    const title = `${item.number} - ${item[`alias_${lang}`]}`;
                    return (
                      <Select.Option
                        key={item.id}
                        value={item.id}
                        label={title}
                      >
                        {title}
                      </Select.Option>
                    );
                  })}
                </Select>
                {offenseForm.response_id && !responseInputVisible ? (
                  <div
                    className="selected-article"
                    style={{ marginBottom: 7 }}
                    onClick={() => {
                      if (canStatusBeChanged) {
                        setResponseInputVisible(true);
                        setTimeout(() => {
                          responseSelectRef.current.focus();
                        });
                      }
                    }}
                  >
                    <div className="article-number">
                      {responsesByID[offenseForm.response_id]?.number}
                    </div>
                    <div>
                      {responsesByID[offenseForm.response_id]?.title}
                      <p>
                        {
                          responsesByID[offenseForm.response_id]?.[
                            `text_${lang}`
                          ]
                        }
                      </p>
                    </div>
                  </div>
                ) : null}
                <Checkbox
                  name="extraInfo"
                  onChange={handleCheck}
                  value={offenseForm.extraInfo}
                >
                  {t(
                    "Rad etish sababini tushuntirish uchun qo'shimcha matnni kiritish"
                  )}
                </Checkbox>
                {offenseForm.extraInfo ? (
                  <Input.TextArea
                    className="mt-2"
                    name="description"
                    onChange={handleInput}
                    value={offenseForm.description}
                    placeholder={t(
                      "Rad etish sababini tushuntirish uchun qo'shimcha matn"
                    )}
                  />
                ) : null}
              </Col>
            </>
          ) : null}
          {offenseForm.status === "accepted" ? (
            <>
              <Col className="label mt-3" md={6}>
                <label>{t("Modda")}</label>
              </Col>
              <Col md={18} className="col-left mt-3">
                <Select
                  disabled={!canStatusBeChanged}
                  ref={articleSelectRef}
                  placeholder={t("Moddani tanlang")}
                  onSelect={(val) => handleSelect("article_id", val)}
                  className="select-input"
                  value={offenseForm.article_id}
                  size="large"
                  open={articleInputVisible}
                  onBlur={() => setArticleInputVisible(false)}
                  onFocus={() => setArticleInputVisible(true)}
                  showSearch
                  style={{
                    width: "100%",
                    display:
                      articleInputVisible || !offenseForm.article_id
                        ? "block"
                        : "none",
                  }}
                  filterOption={(input, option) => {
                    return (
                      option.props.label
                        .toLowerCase()
                        .indexOf(input.toLowerCase()) >= 0
                    );
                  }}
                >
                  {articles.map((item) => {
                    const title = `${item.number} - ${item[`alias_${lang}`]}`;
                    return (
                      <Select.Option
                        key={item.id}
                        value={item.id}
                        label={title}
                      >
                        {title}
                      </Select.Option>
                    );
                  })}
                </Select>
                {offenseForm.article_id && !articleInputVisible ? (
                  <div
                    className="selected-article"
                    onClick={() => {
                      if (canStatusBeChanged) {
                        setArticleInputVisible(true);
                        setTimeout(() => {
                          articleSelectRef.current.focus();
                        });
                      }
                    }}
                  >
                    <div className="article-number">
                      {articlesByID[offenseForm.article_id].number}
                      <div className="factor">
                        {articlesByID[offenseForm.article_id].factor} -{" "}
                        {t("BHM")}
                      </div>
                    </div>
                    <div>
                      {articlesByID[offenseForm.article_id]?.title}
                      <p>
                        {articlesByID[offenseForm.article_id]?.[`text_${lang}`]}
                      </p>
                    </div>
                  </div>
                ) : null}
              </Col>
            </>
          ) : null}
        </Row>
        <div className="d-flex">
          {offenseForm.id && (
            <Button
              size="large"
              type="dashed"
              onClick={() => {
                handleModal();
                document
                  .getElementById(`offense_${offenseForm.id}`)
                  .scrollIntoView({
                    block: "center",
                    behavior: "smooth",
                  });
              }}
              style={{
                marginLeft: 12,
                marginRight: "auto",
                marginTop: 20,
                padding: "7px 30px",
              }}
            >
              {t("Показать")}
            </Button>
          )}
          <Button
            size="large"
            type="dashed"
            onClick={handleModal}
            style={{
              marginLeft: "auto",
              marginRight: 12,
              marginTop: 20,
              padding: "7px 30px",
            }}
          >
            {t("Bekor qilish")}
          </Button>
          {isSubmittedByCitizen ? (
            <Button
              size="large"
              type="primary"
              onClick={handleSubmit}
              disabled={!canSave}
              className={`add-btn ${canSave ? "" : "disabled"}`}
              icon={
                existingOffense?.id ? <AiOutlineCheck /> : <AiOutlinePlus />
              }
              style={{ padding: "7px 30px", marginTop: 20 }}
            >
              {existingOffense?.id ? t("Yangilash") : t("Qo'shish")}
            </Button>
          ) : (
            <Popconfirm
              title={() => (
                <div>
                  <h3>{t("Tasdiqlang")}</h3>
                  <div>
                    <span>
                      {t(
                        "Ushbu DRB fuqaro tomondan taqdim etilmagan. Ragbatlantirish mukofoti sizga beriladi."
                      )}
                    </span>
                    <Tooltip
                      placement="right"
                      style={{ marginLeft: 15 }}
                      getPopupContainer={() => containerRef.current}
                    >
                      <a
                        size="small"
                        target="_blank"
                        href={`/staff/reward`}
                        shape="circle"
                        rel="noreferrer"
                        className="reference-button"
                      >
                        ?
                      </a>
                    </Tooltip>
                  </div>
                </div>
              )}
              getPopupContainer={() => containerRef.current}
              placement="topRight"
              visible={isConfirmationVisible}
              overlayStyle={{ marginLeft: "auto" }}
              okText={t("Tasdiqlash")}
              okButtonProps={{ size: "medium" }}
              cancelButtonProps={{ size: "medium" }}
              cancelText={t("Rad etish")}
              onConfirm={handleSubmit}
              onCancel={() => setConfirmationVisible(false)}
            >
              <Button
                size="large"
                type="primary"
                onClick={() => setConfirmationVisible(true)}
                disabled={!canSave}
                className={`add-btn ${canSave ? "" : "disabled"}`}
                icon={
                  existingOffense?.id ? <AiOutlineCheck /> : <AiOutlinePlus />
                }
                style={{ padding: "7px 30px" }}
              >
                {existingOffense?.id ? t("Yangilash") : t("Qo'shish")}
              </Button>
            </Popconfirm>
          )}
        </div>
      </Modal>
    </StyledOffenseModal>
  );
}
