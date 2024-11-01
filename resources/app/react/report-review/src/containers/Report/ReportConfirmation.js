import React, { useEffect, useRef } from "react";
import { Modal, Input, Button, message, Alert, Tooltip } from "antd";
import PropType from "prop-types";
import t from "../../lang";
import axios from "../../utils/axios";
import { canSaveOffense, createErrors } from "../../utils";
import { useMemo } from "react";
import { Confirmation } from "./style";
import { useState } from "react";
import { FiRefreshCw } from "react-icons/fi";
import S from "../../style";
import * as R from "ramda";
import moment from "moment";

export default function ReportConfirmation({
  indexedOffenses,
  reportId,
  report,
}) {
  const parentRef = useRef();
  const [loading, setLoading] = useState(false);
  const [visible, setVisible] = useState(false);

  const offensesToSubmit = useMemo(
    () =>
      R.pipe(
        // Reject all accepted offenses
        R.filter(canSaveOffense),
        // Nullify removable offense ids
        R.map((offense) => {
          if (offense.removable) {
            return R.dissoc("id", offense);
          } else {
            return offense;
          }
        })
      )(indexedOffenses || {}),
    [indexedOffenses]
  );

  const errors = useMemo(() => {
    const offenses = Object.values(indexedOffenses);

    const citizenCreatedOffenses = offenses.filter(
      (offense) => offense?.citizen_id
    );

    const inspectorCreatedOffenses = offenses.filter(
      (offense) => !offense?.citizen_id
    );

    const hasDuplicate = !!inspectorCreatedOffenses.find((inspectorOffense) => {
      return !!citizenCreatedOffenses.find(
        (citizenOffense) =>
          citizenOffense.vehicle_id === inspectorOffense.vehicle_id
      );
    });

    const duplicateErrors =
      R.pipe(
        R.countBy(R.prop("vehicle_id")),
        R.filter((count) => count > 1),
        R.values,
        R.length
      )(inspectorCreatedOffenses) > 1 || hasDuplicate
        ? [
            {
              vehicle_id: {
                name: "vehicle_id",
                filed: "License Plate",
                value: "This Plate Number is duplicated",
              },
            },
          ]
        : [];
    return [
      ...offenses.filter(canSaveOffense).map(createErrors),
      ...duplicateErrors,
    ];
  }, [offensesToSubmit]);

  const handleSave = async () => {
    setLoading(true);
    const requestData = {
      address: report.address,
      district_id: report.district_id,
      offenses: offensesToSubmit,
    };
    try {
      const { data } = await axios.post(
        `/staff/reports/${reportId}/review`,
        requestData
      );
      message.success(t("Ma'lumotlar muvaffaqiyatli saqlandi."));
      setLoading(false);
      // if (process.env.NODE_ENV !== 'development') {
      const getQuery = new URLSearchParams(window.location.search);
      const decodedUrl = getQuery.get("next_url")
        ? decodeURIComponent(getQuery.get("next_url"))
        : "/staff/reports";
      window.location.replace(decodedUrl);
      // }
    } catch (err) {
      console.log(err);
      message.error(err.message, 4);
      setLoading(false);
    }
  };

  const toggleVisibility = () => setVisible((state) => !state);

  const allOffencesToConfirm = useMemo(
    () =>
      Object.values(offensesToSubmit).filter(
        (item) => item.status === "accepted"
      ),
    [offensesToSubmit]
  );

  const canSubmit = useMemo(
    () =>
      Object.values(offensesToSubmit).every(
        (item) => item.status && item.status !== "created"
      ),
    [offensesToSubmit]
  );

  return (
    <S.Container>
      <Confirmation ref={parentRef}>
        <div className="d-flex">
          {errors.flat().length ? (
            <Tooltip
              color={"red"}
              title={t("Barcha majburiy bo'limlarni tanlang!")}
            >
              <Button
                size="large"
                type="primary"
                // className="save-btn ml-auto d-flex"
                className={`save-btn ml-auto d-flex ${
                  errors.length ? "disabled" : ""
                }`}
                icon={<FiRefreshCw />}
                onClick={toggleVisibility}
                disabled={errors.length}
              >
                {t("Ko'rib chiqishni yakunlash")}
              </Button>
            </Tooltip>
          ) : (
            <Button
              size="large"
              type="primary"
              className={`save-btn ${
                R.isEmpty(offensesToSubmit) ? "disabled" : ""
              }`}
              icon={<FiRefreshCw />}
              onClick={() =>
                allOffencesToConfirm.length ? toggleVisibility() : handleSave()
              }
              disabled={R.isEmpty(offensesToSubmit)}
            >
              {t("Ko'rib chiqishni yakunlash")}
            </Button>
          )}
        </div>

        <Modal
          onOk={toggleVisibility}
          onCancel={toggleVisibility}
          visible={visible}
          width={"80%"}
          footer={
            <div className="confirmation-footer">
              <Button type="default" size="large" onClick={toggleVisibility}>
                {t("Bekor qilish")}
              </Button>
              <Button
                type="primary"
                size="large"
                onClick={handleSave}
                loading={loading}
                disabled={!canSubmit}
                className={`add-btn ${canSubmit ? "" : "disabled"}`}
              >
                {t("Tasdiqlash")}
              </Button>
            </div>
          }
          getContainer={() => parentRef.current}
        >
          {/* {
          !canSubmit ?
            <Alert
              // message={t("Hatolik")}
              description={t("Barcha qoidabuzarliklarni tanlang.")}
              type="error"
              className="error-msg"
              showIcon
              style={{ maxWidth: 'max-content', paddingRight: 40, margin: '10px auto 30px' }}
            />
            :
            null
        } */}

          <ul className="offenses-list">
            {allOffencesToConfirm
              .filter(canSaveOffense)
              .map((offense, index) => {
                const {
                  removable,
                  vehicle_id,
                  vehicle_id_img,
                  number,
                } = offense;
                const subTitle = removable
                  ? t("Yangi qoidabuzalik")
                  : t("Qoidabuzarlik") + `-${number}`;
                const potentialErrors = createErrors(offense);

                return (
                  <li key={index} className="offense-item">
                    <h3 className="text-center">{subTitle}</h3>
                    {vehicle_id_img ? (
                      <div className="text-center">
                        <img
                          src={vehicle_id_img}
                          className="plate-number-img"
                          alt="vehicle plate number"
                        />
                      </div>
                    ) : null}

                    <div className="text-center">
                      <Input
                        value={vehicle_id}
                        className="plate-number"
                        style={{ width: "19ch" }}
                        suffix={
                          <div>
                            <img
                              src={
                                "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' viewBox='0 0 500 250'%3E%3Cpath fill='%231eb53a' d='M0 0h500v250H0z'/%3E%3Cpath fill='%230099b5' d='M0 0h500v125H0z'/%3E%3Cpath fill='%23ce1126' d='M0 80h500v90H0z'/%3E%3Cpath fill='%23fff' d='M0 85h500v80H0z'/%3E%3Ccircle cx='70' cy='40' r='30' fill='%23fff'/%3E%3Ccircle cx='80' cy='40' r='30' fill='%230099b5'/%3E%3Cg fill='%23fff' transform='translate(136 64)'%3E%3Cg id='e'%3E%3Cg id='d'%3E%3Cg id='c'%3E%3Cg id='b'%3E%3Cpath id='a' d='M0-6v6h3' transform='rotate(18 0 -6)'/%3E%3Cuse xlink:href='%23a' transform='scale(-1 1)'/%3E%3C/g%3E%3Cuse xlink:href='%23b' transform='rotate(72)'/%3E%3C/g%3E%3Cuse xlink:href='%23b' transform='rotate(-72)'/%3E%3Cuse xlink:href='%23c' transform='rotate(144)'/%3E%3C/g%3E%3Cuse xlink:href='%23d' y='-24'/%3E%3Cuse xlink:href='%23d' y='-48'/%3E%3C/g%3E%3Cuse xlink:href='%23e' x='24'/%3E%3Cuse xlink:href='%23e' x='48'/%3E%3Cuse xlink:href='%23d' x='-48'/%3E%3Cuse xlink:href='%23d' x='-24'/%3E%3Cuse xlink:href='%23d' x='-24' y='-24'/%3E%3C/g%3E%3C/svg%3E"
                              }
                              alt="uzbekistan flag"
                            />
                            <span>UZ</span>
                          </div>
                        }
                      />
                    </div>
                    {potentialErrors.length ? (
                      <Alert
                        type="error"
                        className="error-list"
                        message={
                          <ul>
                            {potentialErrors.map((item, index) => {
                              return <li key={index}>{item.value}</li>;
                            })}
                          </ul>
                        }
                      />
                    ) : null}
                  </li>
                );
              })}
          </ul>
        </Modal>
      </Confirmation>
    </S.Container>
  );
}

ReportConfirmation.propTypes = {
  isVisible: PropType.bool,
  hideModal: PropType.func,
  params: PropType.string,
};
