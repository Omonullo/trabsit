import React, { useRef } from "react";
import { useEffect } from "react";
import { useState } from "react";
import { IMaskInput } from "react-imask";

const masks = [
  {
    mask: "X 000000",
    definitions: { X: /X/ },
    cls: "vehicle_id--green",
  },
  {
    mask: "T 000000",
    definitions: { T: /T/ },
    cls: "vehicle_id--green",
  },
  {
    mask: "D 000000",
    definitions: { D: /D/ },
    cls: "vehicle_id--green",
  },
  {
    mask: "00 M 000000",
    definitions: { M: /M/ },
    cls: "vehicle_id--green",
  },
  {
    mask: "00 H 000000",
    definitions: { H: /H/ },
    cls: "vehicle_id--yellow",
  },
  {
    mask: "00 000 ##",
    definitions: { "#": /[A-Z]/ },
    cls: "vehicle_id--flag",
  },
  {
    mask: "00 000 ###",
    definitions: { "#": /[A-Z]/ },
    cls: "vehicle_id--flag",
  },
  {
    mask: "00 0000 ##",
    definitions: { "#": /[A-Z]/ },
    cls: "vehicle_id--flag",
  },
  {
    mask: "00 # 000 ##",
    definitions: { "#": /[A-Z]/ },
    cls: "vehicle_id--flag",
  },
  {
    mask: "UN 0000",
    definitions: { U: /U/, N: /N/ },
    cls: "vehicle_id--blue",
  },
  {
    mask: "00 0000 MV",
    definitions: { M: /M/, V: /V/ },
    cls: "vehicle_id--flag",
  },
  {
    mask: "00 0000 ##",
    definitions: { "#": /[A-Z]/ },
    cls: "vehicle_id--flag",
  },
  {
    mask: "00 MX 0000",
    definitions: { M: /M/, X: /X/ },
    cls: "vehicle_id--black",
  },
  {
    mask: "CMD 0000",
    definitions: { C: /C/, M: /M/, D: /D/ },
    cls: "vehicle_id--green",
  },
  {
    mask: "PAA 000",
    definitions: { P: /P/, A: /A/ },
    cls: "vehicle_id--spec",
  },
];

export default function PlateNumberMask({
  plateNumber,
  setPlateNumber,
  disabled = false,
}) {
  const [plateNumberProps, setPlateNumberProps] = useState({
    value: plateNumber,
    mask: {},
  });
  const mask = useRef();

  useEffect(() => {
    setPlateNumberProps((state) => ({ ...state, value: plateNumber }));
  }, [plateNumber]);
  return (
    <IMaskInput
      ref={mask}
      mask={masks}
      value={plateNumberProps.value}
      onAccept={(value, mask) => setPlateNumberProps({ value, mask })}
      placeholder="00 A 000 AA"
      prepare={(str) => str.toUpperCase()}
      className={`vehicle_id ${
        plateNumberProps.mask?.masked?.currentMask?.cls ?? "vehicle_id--flag"
      }`}
      onBlur={() => {
        setPlateNumber(mask.current.maskRef._unmaskedValue);
      }}
      name="vehicle_id"
      disabled={disabled}
    />
  );
}
