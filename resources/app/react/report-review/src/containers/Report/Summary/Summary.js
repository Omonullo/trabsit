import React, { useMemo, useContext } from "react";
import { Col, Tabs, Row, Select, Input } from "antd";
import {
  AiOutlineSchedule,
  AiOutlineUser,
  AiOutlineHourglass,
} from "react-icons/ai";
import { FiMapPin } from "react-icons/fi";
import Logs from "./Logs";
import moment from "moment";
import t from "../../../lang";
import StyledComponent from "../style";
import Map from "./Map";
import ReportContext from "../../../context/ReportContext";
import { useState } from "react";
import { useEffect } from "react";

const { TabPane } = Tabs;

export default function Video() {
  const { report, areas, updateReport } = useContext(ReportContext);
  const { district_id, address, citizen, citizen_id } = report;
  const [inputs, setInputs] = useState({ district_id, address });

  useEffect(() => {
    setInputs((state) => ({ ...state, district_id, address }));
  }, [district_id, address]);

  useEffect(() => {
    updateReport(inputs);
  }, [inputs]);

  const titleWithIcon = useMemo(() => {
    return {
      tab1: (
        <div className="tab-icon">
          <AiOutlineSchedule />
          {t("Tafsilotlar")}
        </div>
      ),
      tab2: (
        <div className="tab-icon">
          <AiOutlineUser />
          {t("Arizachi")}
        </div>
      ),
      tab3: (
        <div className="tab-icon">
          <FiMapPin />
          {t("Karta")}
        </div>
      ),
      tab4: (
        <div className="tab-icon">
          <AiOutlineHourglass />
          {t("Ko'rib chiqishlar jurnali")}
        </div>
      ),
    };
  }, []);

  const handleInput = (e, name) => {
    const { value } = e.target;
    setInputs((state) => ({ ...state, [name]: value }));
  };

  const handleSelect = ({ val, name }) =>
    setInputs((state) => ({ ...state, [name]: val }));

  const regions = useMemo(() => {
    return (
      areas.find((item) => item.id === report.area_id) || { districts: [] }
    );
  }, [report, areas]);

  const {
    address: citizenAddress,
    phone,
    create_time,
    first_name,
    last_name,
    middle_name,
  } = citizen || {};

  const rewardType = useMemo(() => {
    if (report?.reward_params?.["no-reward"] === true) {
      return t("😤 Rag'batlantirish kerak emas");
    } else if (report?.reward_params?.fund) {
      return `${t("😇 Xayriya uchun jamg'arma:")} ${
        report?.reward_params?.fund
      }`;
    } else {
      return t("Yashirilgan");
    }
  }, [report.reward_params]);

  return (
    <StyledComponent>
      <Tabs defaultActiveKey="1">
        <TabPane tab={titleWithIcon.tab1} key="1">
          <Row>
            <Col span={24} md={12}>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Arizachi")}
                </label>
                <p className="kt-label-font-color-3">
                  {first_name
                    ? `${last_name} ${first_name} ${middle_name}`
                    : t("Yashirilgan")}
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Qoidabuzarlik sodir etilgan vaqti")}
                </label>
                <p className="kt-label-font-color-3">
                  {moment(report.incident_time).format("DD MMM YYYY, HH:mm")}
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Viloyat yoki shahar")}
                </label>
                <p className="kt-label-font-color-3">{regions?.name}</p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Tuman")}
                </label>
              </div>
              <div>
                <Select
                  placeholder={t("Viloyat")}
                  onChange={(val) => handleSelect({ val, name: "district_id" })}
                  className="select-input"
                  value={inputs.district_id}
                  style={{ maxWidth: 250, width: "100%" }}
                  allowClear
                  size="large"
                  showSearch
                  filterOption={(input, option) => {
                    return (
                      option.props.label
                        .toLowerCase()
                        .indexOf(input.toLowerCase()) >= 0
                    );
                  }}
                >
                  {regions.districts?.map((item) => {
                    return (
                      <Select.Option
                        key={item.id}
                        value={item.id}
                        label={item.name}
                      >
                        {item.name}
                      </Select.Option>
                    );
                  })}
                </Select>
              </div>
            </Col>
            <Col span={24} md={12} className="col-right">
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Rag'batlantirish")}
                </label>
                <p className="kt-label-font-color-3">{rewardType}</p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Ariza yaratilgan vaqti")}
                </label>
                <p className="kt-label-font-color-3">
                  {moment(report.create_time).format("DD MMM YYYY, HH:mm")}
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Boshqa vodieoyozuvlar")}
                </label>
                <p className="kt-label-font-color-3">
                  <a
                    href={`/staff/reports?point_radius=${report.lat},${report.lng}%2C0.5`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {report.nearby_count
                      ? `${t("500m radiusdagi")} (${report.nearby_count})`
                      : null}
                  </a>
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Poselka/mavze/ko'cha/uy")}
                </label>
                <div className="address">
                  <Input
                    size="large"
                    name="address"
                    value={inputs.address}
                    onChange={(e) => handleInput(e, "address")}
                    placeholder={t("Qoida buzarlik manzili")}
                  />
                </div>
              </div>
            </Col>
          </Row>
        </TabPane>
        <TabPane tab={titleWithIcon.tab2} key="2">
          <Row>
            <Col md={12} span={24}>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("To'liq nomi")}
                </label>
                <p className="kt-label-font-color-3">
                  {first_name
                    ? `${last_name} ${first_name} ${middle_name}`
                    : t("Yashirilgan")}
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Telefon raqami")}
                </label>
                <p>
                  {phone ? (
                    <a href={`tel:${phone}`} target="_blank" rel="noreferrer">
                      {phone}
                    </a>
                  ) : (
                    t("Yashirilgan")
                  )}
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Manzil")}
                </label>
                <p className="kt-label-font-color-3">
                  {citizenAddress ? citizenAddress : t("Yashirilgan")}
                </p>
              </div>
            </Col>
            <Col md={12} span={24}>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Ro'yxatdan o'tgan")}
                </label>
                <p className="kt-label-font-color-3">
                  {create_time
                    ? moment(create_time).format("DD MMM YYYY, HH:mm")
                    : t("Yashirilgan")}
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Video yozuvlar soni")}
                </label>
                <p className="kt-label-font-color-3">
                  <a href={`/staff/reports?citizen_id=${citizen_id}`}>
                    {citizen?.stat?.["report-count"]}
                  </a>
                </p>
              </div>
              <div className="content-wrapper">
                <label htmlFor="" className="kt-label-font-color-2">
                  {t("Qoidabuzarliklar soni")}
                </label>
                <p className="kt-label-font-color-3">
                  {citizen?.stat?.["offense-count"]}
                </p>
              </div>
            </Col>
          </Row>
        </TabPane>
        <TabPane tab={titleWithIcon.tab3} key="3" style={{ minHeight: "80vh" }}>
          <Map />
        </TabPane>
        {/* <TabPane tab={titleWithIcon.tab4} key="4">
          <Logs />
        </TabPane> */}
      </Tabs>
    </StyledComponent>
  );
}
