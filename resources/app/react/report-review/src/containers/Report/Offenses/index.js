import React from "react";
import OffenseItem from "./OffenseItem";
import ReportContext from "../../../context/ReportContext";
import { useContext } from "react";
import * as R from "ramda";

export default function Offenses() {
  const { indexedOffenses } = useContext(ReportContext);
  return (
    <>
      {R.pipe(
        R.values,
        R.reject(R.isNil),
        R.map((offense) => {
          if (offense) {
            return <OffenseItem offense={offense} key={offense.id} />;
          }
          return null;
        })
      )(indexedOffenses)}
    </>
  );
}
