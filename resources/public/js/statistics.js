const tableTranslations = {
  processing: t("Подождите..."),
  loadingRecords: t("Загрузка данных..."),
  zeroRecords: t("Данные отсутствуют"),
  emptyTable: t("В таблице отсутствуют данные"),
};

function createPicker(value, onRangeEnter) {
  const callRangeEnter = debounce(onRangeEnter, 600);

  const picker = $(`
     <div class="mr-4">
       <label class="font-weight-lighter"></label>
       <input type="text" class="form-control" autocomplete="off" style="width: 110px">
       <input type="text" class="form-control" autocomplete="off" style="width: 110px">
     </div>
    `)
    .find("label")
    .text(t("Период"))
    .end();

  const startInput = picker.find("input").eq(0);
  const endInput = picker.find("input").eq(1);

  function setValue(startDate, endDate) {
    startInput.data("imask").value = startDate;
    endInput.data("imask").value = endDate;

    const startPicker = startInput.data("daterangepicker");
    startPicker.setStartDate(startDate);
    startPicker.setEndDate(endDate);

    const endPicker = endInput.data("daterangepicker");
    endPicker.setStartDate(startDate);
    endPicker.setEndDate(endDate);
  }

  picker
    .find("input")
    .daterangepicker({
      autoApply: true,
      autoUpdateInput: false,
      alwaysShowCalendars: true,
      locale: timePickerLocale,
      ranges: {
        [t("Сегодня")]: [moment(), moment()],
        [t("Вчера")]: [
          moment().subtract(1, "days"),
          moment().subtract(1, "days"),
        ],
        [t("Последние 3 дня")]: [moment().subtract(2, "days"), moment()],
        [t("Текущий месяц")]: [
          moment().startOf("month"),
          moment().endOf("month"),
        ],
        [t("Предыдущий месяц")]: [
          moment().subtract(1, "month").startOf("month"),
          moment().subtract(1, "month").endOf("month"),
        ],
      },
    })
    .on("apply.daterangepicker", function (_, picker) {
      const startDate = picker.startDate.format("DD.MM.YYYY");
      const endDate = picker.endDate.format("DD.MM.YYYY");

      setValue(startDate, endDate);
      callRangeEnter(startDate + "-" + endDate);
    });

  picker
    .find("input")
    .maskify("00.00.0000")
    .each(function () {
      $(this)
        .data("imask")
        .on("complete", function () {
          const startDate = startInput.val();
          const endDate = endInput.val();

          if (
            moment(startDate, timePickerLocale.format).isValid() &&
            moment(endDate, timePickerLocale.format).isValid()
          ) {
            setValue(startDate, endDate);
            callRangeEnter(startDate + "-" + endDate);
          }
        });
    });

  const [startDate, endDate] = value.split("-");
  setValue(startDate, endDate);

  return picker;
}

function formatMoney(amount, decimalCount = 0, decimal = ".", thousands = " ") {
  try {
    decimalCount = Math.abs(decimalCount);
    decimalCount = isNaN(decimalCount) ? 2 : decimalCount;

    const negativeSign = amount < 0 ? "-" : "";

    let i = parseInt(
      (amount = Math.abs(Number(amount) || 0).toFixed(decimalCount))
    ).toString();

    let j = i.length > 3 ? i.length % 3 : 0;

    return (
      negativeSign +
      (j ? i.substr(0, j) + thousands : "") +
      i.substr(j).replace(/(\d{3})(?=\d)/g, "$1" + thousands) +
      (decimalCount
        ? decimal +
          Math.abs(amount - i)
            .toFixed(decimalCount)
            .slice(2)
        : "")
    );
  } catch (e) {
    console.warn("Failed to format money", amount, e);
  }
}

function fixCellType(cell) {
  if (typeof cell.v === "number") {
    cell.t = "n";
  } else if (typeof cell.v === "boolean") {
    cell.t = "b";
  } else if (cell.v instanceof Date) {
    cell.t = "n";
    cell.z = XLSX.SSF._table[14];
    cell.v = datenum(cell.v);
  } else {
    cell.t = "s";
  }

  return cell;
}

function fillSheet(data) {
  const sheet = {};
  const range = { s: { c: 10000000, r: 10000000 }, e: { c: 0, r: 0 } };

  for (let r = 0; r < data.length; ++r) {
    for (let c = 0; c < data[r].length; ++c) {
      if (data[r][c] === null) continue;

      if (range.s.r > r) range.s.r = r;
      if (range.s.c > c) range.s.c = c;
      if (range.e.r < r) range.e.r = r;
      if (range.e.c < c) range.e.c = c;

      sheet[XLSX.utils.encode_cell({ c: c, r: r })] = fixCellType({
        v: data[r][c],
      });
    }
  }

  if (range.s.c < 10000000) {
    sheet["!ref"] = XLSX.utils.encode_range(range);
  }

  return sheet;
}

function stringToBlob(str, type) {
  const buffer = new ArrayBuffer(str.length);
  const view = new Uint8Array(buffer);
  for (let i = 0; i < str.length; i++) {
    view[i] = str.charCodeAt(i) & 0xff;
  }
  return new Blob([buffer], { type });
}

function rangeToCells(range) {
  const {
    s: { c: c1, r: r1 },
    e: { c: c2, r: r2 },
  } = XLSX.utils.decode_range(range);
  const result = [];
  for (let c = c1; c <= c2; c++) {
    for (let r = r1; r <= r2; r++) {
      result.push(XLSX.utils.encode_cell({ c, r }));
    }
  }
  return result;
}

function createSheet(data, merges, styles, cols, rows) {
  let sheet = fillSheet(data);

  sheet["!merges"] = merges.map((merge) => XLSX.utils.decode_range(merge));
  sheet["!cols"] = cols;
  sheet["!rows"] = rows;

  styles.forEach(([ranges, style]) =>
    ranges.flatMap(rangeToCells).forEach((cell) => (sheet[cell].s = style))
  );

  return sheet;
}

function lastRowRange(data) {
  return XLSX.utils.encode_range(
    { c: 0, r: data.length - 1 },
    { c: data[data.length - 1].length - 1, r: data.length - 1 }
  );
}

function downloadWorkbook(filename, sheet) {
  saveAs(
    stringToBlob(
      XLSX.write(
        {
          SheetNames: ["sheet1"],
          Sheets: { sheet1: sheet },
        },
        {
          bookType: "xlsx",
          bookSST: false,
          type: "binary",
        }
      ),
      "application/octet-stream"
    ),
    filename
  );
}

function downloadAreaOffense(areas, date) {
  let data = [
    [
      t(
        "Приложение к постановлению Кабинета Министров от 20 сентября 2018 года № 747 о порядке принятия и рассмотрения видеозаписей о случаях правонарушений, зафиксированных видеорегистраторами, установленными в автотранспортных средствах физических и юридических лиц, а также поощрения лиц, их предоставивших"
      ),
    ],
    [t("Данные")],
    [t("Дата: %s", date)],
    [
      t("Регион"),
      t("Поступило видеозаписей"),
      t("Поступило нарушений"),
      t("Принято"),
      t("Ожидают"),
      t("Отклонено"),
      t("Выписано"),
      "",
      t("Оплачено"),
      "",
      t("Неоплачено"),
      "",
      t("Просрочено"),
      "",
    ],
    [
      "",
      "",
      "",
      "",
      "",
      "",
      t("Число"),
      t("Сумма"),
      t("Число"),
      t("Сумма"),
      t("Число"),
      t("Сумма"),
      t("Число"),
      t("Сумма"),
    ],
    ...areas.map((a) => [
      a.area_name,
      a.report_count,
      a.count,
      a.accepted_count,
      a.pending_count,
      a.rejected_count,
      a.fine_count,
      a.fine_sum,
      a.paid_fine_count,
      a.paid_fine_sum,
      a.unpaid_fine_count,
      a.unpaid_fine_sum,
      a.expired_fine_count,
      a.expired_fine_sum,
    ]),
    [
      t("Всего"),
      areas.reduce((acc, area) => acc + area.report_count, 0),
      areas.reduce((acc, area) => acc + area.count, 0),
      areas.reduce((acc, area) => acc + area.accepted_count, 0),
      areas.reduce((acc, area) => acc + area.pending_count, 0),
      areas.reduce((acc, area) => acc + area.rejected_count, 0),
      areas.reduce((acc, area) => acc + area.fine_count, 0),
      areas.reduce((acc, area) => acc + area.fine_sum, 0),
      areas.reduce((acc, area) => acc + area.paid_fine_count, 0),
      areas.reduce((acc, area) => acc + area.paid_fine_sum, 0),
      areas.reduce((acc, area) => acc + area.unpaid_fine_count, 0),
      areas.reduce((acc, area) => acc + area.unpaid_fine_sum, 0),
      areas.reduce((acc, area) => acc + area.expired_fine_count, 0),
      areas.reduce((acc, area) => acc + area.expired_fine_sum, 0),
    ],
  ];

  downloadWorkbook(
    "offense.xlsx",
    createSheet(
      data,
      [
        "A1:N1",
        "A2:N2",
        "A3:N3",
        "A4:A5",
        "B4:B5",
        "C4:C5",
        "D4:D5",
        "E4:E5",
        "F4:F5",
        "G4:H4",
        "I4:J4",
        "K4:L4",
        "M4:N4",
      ],
      [
        [
          ["A1:A2", "A4:N5"],
          {
            alignment: {
              horizontal: "center",
              vertical: "center",
              wrapText: true,
            },
          },
        ],
        [
          ["A3"],
          {
            alignment: {
              horizontal: "right",
              vertical: "center",
            },
          },
        ],
        [
          [lastRowRange(data)],
          {
            font: {
              bold: true,
            },
          },
        ],
      ],
      [
        { wch: 34 },
        { wch: 17 },
        { wch: 17 },
        { wch: 17 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
      ],
      [{ hpx: 90 }, {}, {}, { hpx: 40 }]
    )
  );
}

function downloadOffenseFunnel(rows, date) {
  let data = [
    [t("Дата: %s", date)],
    [
      t("Регион"),
      t("videoJarima"),
      "",
      "",
      "",
      t("КСУБД"),
      "",
      "",
      "",
      "",
      t("Оплачено"),
      "",
      t("Неоплачено"),
      "",
      t("Просрочено"),
      "",
    ],
    [
      "",
      t("Поступило нарушений"),
      t("Ожидают"),
      t("Отклонено"),
      t("Принято"),
      t("Не доставлено"),
      t("Доставлено"),
      t("Ожидают оформления"),
      t("Отклонено судом"),
      t("Оформлено"),
      t("Число"),
      t("Сумма"),
      t("Число"),
      t("Сумма"),
      t("Число"),
      t("Сумма"),
    ],
    ...rows.map((a) => [
      a.area_name,
      a.count || 0,
      a.pending_count || 0,
      a.rejected_count || 0,
      a.accepted_count || 0,
      a.failed_count || 0,
      a.forwarded_count || 0,
      a.pending_fined_count || 0,
      a.dismissed_count || 0,
      a.fined_count || 0,
      a.paid_fine_count || 0,
      a.paid_fine_sum || 0,
      a.unpaid_fine_count || 0,
      a.unpaid_fine_sum || 0,
      a.expired_fine_count || 0,
      a.expired_fine_sum || 0,
    ]),
    [
      t("Всего"),
      rows.reduce((acc, area) => acc + area.count, 0),
      rows.reduce((acc, area) => acc + area.pending_count, 0),
      rows.reduce((acc, area) => acc + area.rejected_count, 0),
      rows.reduce((acc, area) => acc + area.accepted_count, 0),
      rows.reduce((acc, area) => acc + area.failed_count, 0),
      rows.reduce((acc, area) => acc + area.forwarded_count, 0),
      rows.reduce((acc, area) => acc + area.pending_fined_count, 0),
      rows.reduce((acc, area) => acc + area.dismissed_count, 0),
      rows.reduce((acc, area) => acc + area.fined_count, 0),
      rows.reduce((acc, area) => acc + area.paid_fine_count, 0),
      rows.reduce((acc, area) => acc + area.paid_fine_sum, 0),
      rows.reduce((acc, area) => acc + area.unpaid_fine_count, 0),
      rows.reduce((acc, area) => acc + area.unpaid_fine_sum, 0),
      rows.reduce((acc, area) => acc + area.expired_fine_count, 0),
      rows.reduce((acc, area) => acc + area.expired_fine_sum, 0),
    ],
  ];

  downloadWorkbook(
    "offense-funnel.xlsx",
    createSheet(
      data,
      ["A1:P1", "A2:A3", "B2:E2", "F2:J2", "K2:L2", "M2:N2", "O2:P2"],
      [
        [
          ["A1", "A2:P3"],
          {
            alignment: {
              horizontal: "center",
              vertical: "center",
              wrapText: true,
            },
          },
        ],
        [
          [lastRowRange(data)],
          {
            font: {
              bold: true,
            },
          },
        ],
      ],
      [
        { wch: 34 },
        { wch: 17 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 17 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
      ],
      [{ hpx: 40 }, { hpx: 40 }, { hpx: 40 }]
    )
  );
}

function downloadArticleFunnel(rows, date, area_name) {
  let data = [
    [t("Дата: %s", date)],
    [
      t(
        "Ўзбекистон Республикаси Вазирлар Маҳкамасининг 2018 йил 20 сентябрдаги 747-сон Қарори асосида жисмоний ва юридик шахсларнинг автотранспорт воситаларига ўрнатилган видеорегистраторлар орқали қайд этилган қоидабузарлик ҳолатлари. Моддалар кесимида қоидабузарликлар статуси"
      ),
    ], //[area_name ? t("Регион: %s", area_name) : ""],
    [
      t("Статья"),
      t("Краткое называние"),
      t("Приняты инспектором. Из этого → "),
      t("Ожидание номера постановления"),
      t("Не доставлен"),
      t("Отменен"),
      t("Оплачены"),
      "",
      t("Не оплачены"),
      "",
      t("Просрочены"),
      "",
    ],
    [
      "",
      "",
      "",
      "",
      "",
      "",
      t("Число"),
      t("Сумма"),
      t("Число"),
      t("Сумма"),
      t("Число"),
      t("Сумма"),
    ],
    [
      "",
      "",
      t(
        "Правонарушения которые были приняты инспектором и применены статьи кодекса"
      ),
      t(
        "Правонарушения которые успешно отправлены и получены КСУБД, но ejarima не получил номер постановления"
      ),
      t(
        "Правонарушения не были приняты КСУБД в основном по причине отсутствия номера авто в базе или другие тех.причины"
      ),
      t(
        "КСУБД принял дал номер постановления, потом отменил. Причина отмены не известно"
      ),
      t(
        "Оплаченные правонарушения. Одно постановление может быть и в Отменен и в оплачен. Сумма не учитывает %s скидку на штраф",
        "30%󠀥󠀥󠀥"
      ),
      "",
      t(
        "Со дня рассмотрения инспектором правонарушения статус ''ожидание оплаты'' меньше чем 60 дней."
      ),
      "",
      t(
        "Со дня рассмотрения инспектором правонарушения статус ''ожидание оплаты'' больше чем 60 дней."
      ),
      "",
    ],
    ...rows.map((article) => [
      article.number,
      article.alias,
      article.accepted_count,
      article.pending_fined_count,
      article.failed_count,
      article.dismissed_count,
      article.paid_fine_count,
      article.paid_fine_sum,
      article.unpaid_fine_count,
      article.unpaid_fine_sum,
      article.expired_fine_count,
      article.expired_fine_sum,
    ]),
    [
      t("Всего"),
      "",
      rows.reduce((acc, article) => acc + article.accepted_count, 0),
      rows.reduce((acc, article) => acc + article.pending_fined_count, 0),
      rows.reduce((acc, article) => acc + article.failed_count, 0),
      rows.reduce((acc, article) => acc + article.dismissed_count, 0),
      rows.reduce((acc, article) => acc + article.paid_fine_count, 0),
      rows.reduce((acc, article) => acc + article.paid_fine_sum, 0),
      rows.reduce((acc, article) => acc + article.unpaid_fine_count, 0),
      rows.reduce((acc, article) => acc + article.unpaid_fine_sum, 0),
      rows.reduce((acc, article) => acc + article.expired_fine_count, 0),
      rows.reduce((acc, article) => acc + article.expired_fine_sum, 0),
    ],
  ];
  downloadWorkbook(
    "ejarima-offense-article-report.xlsx",
    createSheet(
      data,
      [
        "A1:L1", // for date
        "A2:L2", // for region
        "A3:A4", // for article number
        "B3:B4", // for article alias
        "C3:C4", // for total accepted
        "D3:D4", // for pending fine
        "E3:E4", // for failed offenses
        "F3:F4", // for dismissed
        "G3:H3", // for paid
        "I3:J3", // for not paid
        "K3:L3", // for expired
        "G5:H5",
        "I5:J5",
        "K5:L5",
      ],
      [
        [
          ["A1", "A2", "A3:L4", "A5:L5"],
          {
            alignment: {
              horizontal: "center",
              vertical: "center",
              wrapText: true,
            },
          },
        ],
        [
          [lastRowRange(data)],
          {
            font: {
              bold: true,
            },
          },
        ],
      ],
      [
        { wch: 15 },
        { wch: 34 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 17 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
      ],
      [{ hpx: 40 }, { hpx: 40 }, { hpx: 40 }, {}, { hpx: 60 }]
    )
  );
}

function downloadFineReward(fine, date) {
  let data = [
    [t("Дата: %s", date)],
    [
      "№",
      t("Оплаченные штрафы"),
      "",
      "",
      t("Выплаченные вознаграждения"),
      "",
      "",
    ],
    [
      "",
      t("Штраф"),
      t("Кол-во"),
      t("Сумма"),
      t("Вознаграждение"),
      t("Кол-во"),
      t("Сумма"),
    ],
    ...fine.map((a) => [
      a.number,
      a.fine,
      a.fine_count,
      a.fine_sum,
      a.reward,
      a.reward_count,
      a.reward_sum,
    ]),
    [
      t("Всего"),
      fine.reduce((acc, fine) => acc + fine.fine, 0),
      fine.reduce((acc, fine) => acc + fine.fine_count, 0),
      fine.reduce((acc, fine) => acc + fine.fine_sum, 0),
      fine.reduce((acc, fine) => acc + fine.reward, 0),
      fine.reduce((acc, fine) => acc + fine.reward_count, 0),
      fine.reduce((acc, fine) => acc + fine.reward_sum, 0),
    ],
  ];

  downloadWorkbook(
    "fines-rewards.xlsx",
    createSheet(
      data,
      ["A1:G1", "A2:A3", "B2:D2", "E2:G2"],
      [
        [
          ["A1:A2", "A3:B3", "C3:D3", "E3:F3", "G3", "B2", "E2"],
          {
            alignment: {
              horizontal: "center",
              vertical: "center",
              wrapText: true,
            },
          },
        ],
        [
          [lastRowRange(data)],
          {
            font: {
              bold: true,
            },
          },
        ],
      ],
      [
        { wch: 10 },
        { wch: 17 },
        { wch: 17 },
        { wch: 17 },
        { wch: 15 },
        { wch: 15 },
        { wch: 15 },
      ],
      []
    )
  );
}

function renderMoney(data) {
  return formatMoney(data);
}

function footerStats(columns, startFrom = 1) {
  return function (row, data, start, end, display) {
    let api = this.api();
    columns.slice(startFrom).map((column, index) => {
      $(api.column(index + startFrom).footer()).html(
        formatMoney(
          display
            .map((index) => data[index][column.data])
            .reduce((a, b) => a + b, 0)
        )
      );
    });
  };
}

function areaOffenseStats(table) {
  let columns = [
    { data: "area_name", className: "no-wrap" },
    {
      data: "report_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "accepted_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "pending_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "rejected_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "paid_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "paid_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "unpaid_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "unpaid_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "expired_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "expired_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
  ];
  let dateRange =
    moment().date(1).format("DD.MM.YYYY") + "-" + moment().format("DD.MM.YYYY");

  table
    .DataTable({
      dom: "Bfrtip",
      buttons: ["excelHtml5"],
      language: tableTranslations,
      order: [],
      paging: false,
      scrollX: true,
      info: false,
      ajax: {
        url: "/api/staff/statistics/offenses",
        dataSrc: "",
        data: function () {
          return { date_range: dateRange };
        },
      },
      columns: columns,
      footerCallback: footerStats(columns),

      initComplete: function (setting, data) {
        let tableButton = $("#area_offense_table_wrapper .buttons-excel");
        tableButton.unbind();
        tableButton.on("click", () =>
          downloadAreaOffense(table.DataTable().data().toArray(), dateRange)
        );
      },
    })
    .on("init", function (e, setting) {
      let picker = createPicker(dateRange, function (v) {
        dateRange = v;
        setting.oInstance.api().ajax.reload();
      });
      $("#area_offense_table_filter").prepend(picker);
    });
}

function offenseFunnelStats(table) {
  let columns = [
    { data: "area_name", className: "no-wrap" },
    {
      data: "count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "pending_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "rejected_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "accepted_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "failed_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "forwarded_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "pending_fined_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "dismissed_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "fined_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "paid_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "paid_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "unpaid_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "unpaid_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "expired_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "expired_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
  ];
  let dateRange =
    moment().date(1).format("DD.MM.YYYY") + "-" + moment().format("DD.MM.YYYY");
  table
    .DataTable({
      dom: "Bfrtip",
      buttons: ["excelHtml5"],
      language: tableTranslations,
      order: [],
      paging: false,
      scrollX: true,
      info: false,
      ajax: {
        url: "/api/staff/statistics/offenses-funnel",
        dataSrc: "",
        data: function () {
          return { date_range: dateRange };
        },
      },
      columns: columns,
      footerCallback: footerStats(columns),

      initComplete: function (setting, data) {
        let tableButton = $("#offense_funnel_table_wrapper .buttons-excel");
        tableButton.unbind();
        tableButton.on("click", () => {
          downloadOffenseFunnel(table.DataTable().data().toArray(), dateRange);
        });
      },
    })
    .on("init", function (e, setting) {
      let picker = createPicker(dateRange, function (v) {
        dateRange = v;
        setting.oInstance.api().ajax.reload();
      });
      $("#offense_funnel_table_filter").prepend(picker);
    });
}

function articleFunnelStats(table) {
  let columns = [
    { data: "number", className: "no-wrap" },
    {
      data: "alias",
      className: "no-wrap",
      createdCell: function (td, cellData, rowData, row, col) {
        $(td).popover({
          placement: "top",
          trigger: "hover",
          delay: 500,
          content: rowData.text,
        });
      },
    },
    {
      data: "accepted_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "pending_fined_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "failed_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "dismissed_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "paid_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "paid_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "unpaid_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "unpaid_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "expired_fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "expired_fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
  ];
  let area_id = $("[data-default-article-area-id]").data(
    "default-article-area-id"
  );
  let dateRange =
    moment().date(1).format("DD.MM.YYYY") + "-" + moment().format("DD.MM.YYYY");
  let dTable = table.DataTable({
    dom: "Bfrtip",
    buttons: ["excelHtml5"],
    language: tableTranslations,
    order: [],
    paging: false,
    scrollX: true,
    info: false,
    ajax: {
      url: "/api/staff/statistics/articles-funnel",
      dataSrc: "",
      data: function () {
        return { date_range: dateRange, area_id };
      },
    },
    columns: columns,
    footerCallback: footerStats(columns, 2),

    initComplete: function (setting, data) {
      let tableButton = $("#article_funnel_table_wrapper .buttons-excel");
      tableButton.unbind();
      tableButton.on("click", () => {
        downloadArticleFunnel(table.DataTable().data().toArray(), dateRange);
      });
    },
  });
  dTable.on("init", function (e, setting) {
    let picker = createPicker(dateRange, function (v) {
      dateRange = v;
      setting.oInstance.api().ajax.reload();
    });
    $(".article-area-select").on("click", function () {
      $(".article-area-dropdown .kt-nav__link-text").html($(this).html());
      area_id = $(this).data("area-id");
      dTable.ajax.reload();
    });
    $("#article_funnel_table_filter").prepend(picker);
  });
}

function fineRewardsStats(table) {
  let columns = [
    { data: "number", className: "no-wrap" },
    {
      data: "fine",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "fine_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "fine_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "reward",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "reward_count",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
    {
      data: "reward_sum",
      className: "text-right no-wrap",
      render: renderMoney,
      type: "money",
    },
  ];

  const rewardTypeDom = $(`<div class="mx-2 mt-1">
        <a class="btn btn-bold btn-sm dropdown-toggle btn-label-brand reward-type-dropdown"
           aria-expanded="false"
           data-toggle="dropdown"
           data-default-area-id="{{ default-area.id }}">
          <span class="kt-nav__link-text">Все</span>
        </a>
       
        <div class="dropdown-menu dropdown-menu-fit dropdown-menu-right" x-placement="bottom-end"
             style="position: absolute; will-change: transform; top: 0px; left: 0px; transform: translate3d(1353px, 46px, 0px);">

          <ul class="kt-nav">
            <li class="kt-nav__item">
              <a class="kt-nav__link reward-type-select">
                <span class="kt-nav__link-text">Все</span>
              </a>
            </li>
            <li class="kt-nav__item">
              <a class="kt-nav__link reward-type-select" data-reward-type="no-reward">
                <span class="kt-nav__link-text">Без вознаграждения</span>
              </a>
            </li>
             <li class="kt-nav__item">
              <a class="kt-nav__link reward-type-select" data-reward-type="inspector">
                <span class="kt-nav__link-text">Инспектору</span>
              </a>
            </li>
            <li class="kt-nav__item">
              <a class="kt-nav__link reward-type-select" data-reward-type="fund">
                <span class="kt-nav__link-text">Благотворительный фонд</span>
              </a>
            </li>
            <li class="kt-nav__item">
              <a class="kt-nav__link reward-type-select" data-reward-type="phone">
                <span class="kt-nav__link-text">Телефон</span>
              </a>
            </li>
            <li class="kt-nav__item">
              <a class="kt-nav__link reward-type-select" data-reward-type="bank">
                <span class="kt-nav__link-text">Банк</span>
              </a>
            </li>
            <li class="kt-nav__item">
              <a class="kt-nav__link reward-type-select" data-reward-type="card">
                <span class="kt-nav__link-text">Карта</span>
              </a>
            </li>
          </ul>
        </div>
      </div>`);
  let rewardType = "";
  let dateRange =
    moment().date(1).format("DD.MM.YYYY") + "-" + moment().format("DD.MM.YYYY");
  table
    .DataTable({
      dom: "Bfrtip",
      buttons: ["excelHtml5"],
      language: tableTranslations,
      order: [],
      paging: false,
      scrollX: true,
      info: false,
      ajax: {
        url: "/api/staff/statistics/fines-rewards",
        dataSrc: "",
        data: function () {
          return { date_range: dateRange, reward_type: rewardType };
        },
      },
      columns: columns,
      footerCallback: footerStats(columns),

      initComplete: function () {
        let tableButton = $("#fine_reward_table_wrapper .buttons-excel");
        tableButton.unbind();
        tableButton.on("click", () =>
          downloadFineReward(table.DataTable().data().toArray(), dateRange)
        );
      },
    })
    .on("init", function (e, setting) {
      let picker = createPicker(dateRange, function (v) {
        dateRange = v;
        setting.oInstance.api().ajax.reload();
      });
      const fineFilter = $("#fine_reward_table_filter");
      fineFilter.append(rewardTypeDom);
      fineFilter.prepend(picker);
      $(rewardTypeDom)
        .find(".reward-type-select")
        .on("click", function () {
          rewardType = $(this).data("reward-type") || "";
          setting.oInstance.api().ajax.reload();
          rewardTypeDom.find(".reward-type-dropdown").html($(this).html());
        });
    });
}

function expiryStats(table) {
  let expiry_column_names = [
    "area_name",
    "age_3",
    "age_4",
    "age_5",
    "age_6",
    "age_7",
    "age_8",
    "age_9",
    "age_10",
    "age_11",
    "age_12",
    "age_13",
  ];

  table
    .DataTable({
      dom: "Bfrtip",
      buttons: ["excelHtml5", "csvHtml5"],
      language: tableTranslations,
      paging: false,
      info: false,
      ajax: {
        url: "/api/staff/statistics/expired-reports",
        dataSrc: "",
      },
      columns: expiry_column_names.map((col) => ({ data: col })),

      footerCallback: function (row, data, start, end, display) {
        let api = this.api();
        expiry_column_names.slice(1).map((name, index) => {
          $(api.column(index + 1).footer()).html(
            display.map((index) => data[index][name]).reduce((a, b) => a + b, 0)
          );
        });
      },
    })
    .on("init", () => {
      $(".expired-overall-stats").append(
        $('<span class="ml-2">').text(
          t("Всего просрочено %s", $(".total-expired-count").text())
        )
      );
    });
}

function reviewedReportStats(table) {
  let reviewed_reports_column_names = [
    "area_name",
    "inspector_name",
    "count_1",
    "count_2",
    "count_3",
    "count_4",
    "total",
  ];

  table
    .DataTable({
      dom: "Bfrtip",
      buttons: ["excelHtml5", "csvHtml5"],
      language: tableTranslations,
      paging: false,
      info: false,
      ajax: {
        url: "/api/staff/statistics/reviewed-reports",
        dataSrc: "",
      },
      columns: reviewed_reports_column_names.map((col) => ({ data: col })),
      footerCallback: function (row, data, start, end, display) {
        let api = this.api();
        reviewed_reports_column_names.slice(2).map((name, index) => {
          $(api.column(index + 2).footer()).html(
            display.map((index) => data[index][name]).reduce((a, b) => a + b, 0)
          );
        });
      },
    })
    .on("preDraw", function () {
      $(".grouped_area").each(function () {
        $(this)
          .removeClass("grouped_area")
          .show()
          .data("first_instance")
          .attr("rowspan", 1);
      });
    })
    .on("draw", () => {
      let first_occurrence = null;
      let rowspan = 1;
      table.find("tr").each(function () {
        let area_name = $(this).find("td:first");
        if (first_occurrence === null) {
          first_occurrence = area_name;
        } else if (area_name.text() === first_occurrence.text()) {
          area_name.hide();
          area_name.addClass("grouped_area");
          area_name.data("first_instance", first_occurrence);
          ++rowspan;
          first_occurrence.attr("rowspan", rowspan);
        } else {
          first_occurrence = area_name;
          rowspan = 1;
        }
      });
    })
    .on("init", () => {
      $(".reviewed-overall-stats").append(
        $('<span class="m-3">').text(
          t("Всего %s", $(".total-reviewed-count").text())
        )
      );
    });
}

function inspectorReportsChart(data, area_id) {
  let inspectors = data.filter((inspector) => inspector.area_id === area_id);

  Highcharts.chart("reviewed-reports-chart", {
    credits: false,
    chart: {
      type: "column",
    },
    title: {
      text: "",
    },
    xAxis: {
      categories: inspectors.map(({ inspector_name }) => inspector_name),
      crosshair: true,
    },
    yAxis: {
      min: 0,
      title: {
        text: t("Кол-во рассмотренных видеозаписей"),
      },
      stackLabels: {
        enabled: true,
        style: {
          color: (Highcharts.theme && Highcharts.theme.textColor) || "gray",
        },
      },
    },
    legend: {
      align: "left",
      x: 0,
      y: 0,
      verticalAlign: "bottom",
      itemStyle: {
        fontWeight: "normal",
      },
      backgroundColor: "white",
      borderColor: "#CCC",
      borderWidth: 1,
      shadow: false,
    },
    tooltip: {
      formatter: function () {
        return `${this.x} <br/> ${t("Рассмотренные")}: <br/>
            ${this.points.reduce(
              (acc, point) =>
                acc +
                t("%s: %d видеозаписей", point.series.name, point.y) +
                "<br/>",
              ""
            )}`;
      },
      shared: true,
    },
    plotOptions: {
      column: {
        column: {
          borderWidth: 0,
        },
      },
    },
    series: [
      {
        name: t("За сегодня"),
        data: inspectors.map(({ count_1 }) => count_1),
      },
      {
        name: t("За 3 дня"),
        data: inspectors.map(({ count_2 }) => count_2),
      },
      {
        name: t("За 7 дней"),
        data: inspectors.map(({ count_3 }) => count_3),
      },
      {
        name: t("За 30 дней"),
        data: inspectors.map(({ count_4 }) => count_4),
      },
    ],
  });
}

function expiredReportsChart(areas) {
  Highcharts.chart("expiry-chart", {
    credits: false,
    chart: {
      type: "column",
    },
    title: {
      text: "",
    },
    xAxis: {
      categories: Array.apply(null, Array(11)).map((_, index) => {
        let number = index + 1;
        if (number === 1) return t("на 1 день");
        else if (number < 5) return t("на %d дня", number);
        else if (number === 11) return t("на %d дней и более", number);
        else return t("на %d дней", number);
      }),
    },
    yAxis: {
      min: 0,
      title: {
        text: "",
      },
      stackLabels: {
        enabled: true,
        style: {
          color: (Highcharts.theme && Highcharts.theme.textColor) || "gray",
        },
      },
    },
    legend: {
      align: "left",
      x: 0,
      y: 0,
      verticalAlign: "bottom",
      itemStyle: {
        fontWeight: "normal",
      },
      backgroundColor: "white",
      borderColor: "#CCC",
      borderWidth: 1,
      shadow: false,
    },

    tooltip: {
      formatter: function () {
        return `<b>${this.point.series.name}</b><br/>
                    ${this.x}:  ${t("%d видеозаписей", this.point.y)}<br/>
                    ${t("Всего: %d видеозаписей", this.point.stackTotal)}`;
      },
    },

    plotOptions: {
      column: {
        stacking: "normal",
        dataLabels: {
          enabled: false,
          color: "#35bfa2" || "#f3f3fb" || "white",
        },
      },
    },
    series: areas.map(function (area) {
      return {
        name: area.area_name,
        dataLabels: {
          fontWeight: "normal",
        },

        states: {
          hover: {
            enabled: false,
          },
        },
        data: Array.apply(null, Array(11)).map(
          (_, index) => area["age_" + (index + 3)]
        ),
      };
    }),
  });
}

function restyleTableWrapper(wrapper) {
  let searchInput = wrapper
    .find(".dataTables_filter label input")
    .addClass("ml-0")
    .addClass("form-control");

  let inputGroup = $('<div style="width: fit-content">')
    .attr("class", "input-group ml-3")
    .append(
      $('<div class="input-group-prepend">').append(
        $('<span class="input-group-text">').append(
          $('<i class="flaticon-search">')
        )
      )
    )
    .append(searchInput);

  wrapper
    .find(".dataTables_filter")
    .addClass("d-flex justify-content-end")
    .append(wrapper.find(".dt-buttons"))
    .append(inputGroup)
    .find("label")
    .remove();

  wrapper.find(".buttons-excel").prepend('<i class="fa fa-file-download">');
  wrapper.find(".buttons-csv").prepend('<i class="fa fa-file-download">');
}

$(function () {
  jQuery.extend(jQuery.fn.dataTableExt.oSort, {
    "money-pre": function (a) {
      return parseFloat(a.replace(/ /g, ""));
    },
    "money-asc": function (a, b) {
      return a < b ? -1 : a > b ? 1 : 0;
    },
    "money-desc": function (a, b) {
      return a < b ? 1 : a > b ? -1 : 0;
    },
  });

  areaOffenseStats($("#area_offense_table"));
  offenseFunnelStats($("#offense_funnel_table"));
  articleFunnelStats($("#article_funnel_table"));
  fineRewardsStats($("#fine_reward_table"));
  expiryStats($("#expiry_table"));
  reviewedReportStats($("#reviewed_reports_table"));

  $(".dataTables_wrapper").each(function () {
    restyleTableWrapper($(this));
  });

  $.get("/api/staff/statistics/expired-reports").done(function (areas) {
    expiredReportsChart(areas);
  });

  $.get("/api/staff/statistics/reviewed-reports").done(function (data) {
    inspectorReportsChart(
      data,
      $("[data-default-area-id]").data("default-area-id")
    );
    $(".area-select").on("click", function (_) {
      $(".area-dropdown").html($(this).html());
      inspectorReportsChart(data, $(this).data("area-id") || null);
    });
  });
});
