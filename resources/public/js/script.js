function init_map() {}

$.fn.spin = function () {
  return $(this).each(function () {
    if (!$(this).data("is-spinning"))
      $(this)
        .data("is-spinning", true)
        .closest(".kt-input-icon")
        .addClass("kt-input-icon--right")
        .append(
          $(
            '<span class="kt-input-icon__icon kt-input-icon__icon--right">\n' +
              "  <span>\n" +
              '    <span class="spinner-border spinner-border-md text-secondary"></span>\n' +
              "  </span>\n" +
              "</span>"
          )
        );
  });
};

$.fn.btn_spin = function () {
  return $(this).each(function () {
    if (!$(this).data("is-spinning"))
      $(this)
        .data("content", $(this).html())
        .data("is-spinning", true)
        .html(
          $(
            "<span>\n" +
              '  <span class="spinner-border spinner-border-md text-secondary"></span>\n' +
              "</span>\n"
          )
        );
  });
};

$.fn.btn_unspin = function () {
  return $(this).each(function () {
    if ($(this).data("is-spinning"))
      $(this).data("is-spinning", false).html($(this).data("content"));
  });
};

$.fn.unspin = function () {
  return $(this).each(function () {
    if ($(this).data("is-spinning"))
      $(this)
        .data("is-spinning", false)
        .closest(".kt-input-icon")
        .removeClass("kt-input-icon--right")
        .find(".kt-input-icon__icon--right")
        .remove();
  });
};

$.fn.invalid = function (message) {
  return $(this).each(function (i, element) {
    if (message) {
      element.setCustomValidity(message);
      element.reportValidity();
    } else {
      element.setCustomValidity("");
    }
  });
};

$.fn.maskify = function (mask) {
  return $(this).each(function (i, element) {
    const imask = new IMask(element, {
      mask: mask,
      prepare: function (str) {
        return str.toUpperCase();
      },
    });
    $(element).data("imask", imask);
  });
};

$.fn.video = function (file) {
  return $(this).each(function (i, element) {
    if (file) {
      $(element).attr("src", URL.createObjectURL(file)).slideDown();
    } else {
      element.pause();
      element.removeAttribute("src");
      element.load();
      $(element).slideUp();
    }
  });
};

$.fn.gallery = function (index) {
  new PhotoSwipe(
    document.getElementById("pswp"),
    PhotoSwipeUI_Default,
    $(this)
      .map(function () {
        return {
          src: $(this).attr("src"),
          w:
            (window.screen.availHeight * $(this).prop("naturalWidth")) /
            $(this).prop("naturalHeight"),
          h: window.screen.availHeight,
        };
      })
      .toArray(),
    { index }
  ).init();
};

function size(bytes) {
  if (Math.abs(bytes) < 1000) {
    return bytes + " B";
  }
  let units = ["kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"];
  let u = -1;
  do {
    bytes /= 1000;
    ++u;
  } while (Math.abs(bytes) >= 1000 && u < units.length - 1);
  return bytes.toFixed(1) + " " + units[u];
}

function escape(unsafeText) {
  let div = document.createElement("div");
  div.innerText = unsafeText;
  return div.innerHTML;
}

$.fn.datetimepicker.dates["ru"] = {
  days: [
    "Воскресенье",
    "Понедельник",
    "Вторник",
    "Среда",
    "Четверг",
    "Пятница",
    "Суббота",
  ],
  daysShort: ["Вск", "Пнд", "Втр", "Срд", "Чтв", "Птн", "Суб"],
  daysMin: ["Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"],
  months: [
    "Январь",
    "Февраль",
    "Март",
    "Апрель",
    "Май",
    "Июнь",
    "Июль",
    "Август",
    "Сентябрь",
    "Октябрь",
    "Ноябрь",
    "Декабрь",
  ],
  monthsShort: [
    "Янв",
    "Фев",
    "Мар",
    "Апр",
    "Май",
    "Июн",
    "Июл",
    "Авг",
    "Сен",
    "Окт",
    "Ноя",
    "Дек",
  ],
  today: "Сегодня",
  clear: "Очистить",
  format: "dd.mm.yyyy",
  meridiem: [],
  weekStart: 1,
  monthsTitle: "Месяцы",
};

function scrollTop() {
  return (
    window.pageYOffset ||
    document.documentElement.scrollTop ||
    document.body.scrollTop ||
    0
  );
}

function togglePlayerFullscreen(player) {
  if (player.isFullScreen) {
    document.exitFullscreen();
  } else {
    player.requestFullscreen();
  }
}

function format_seconds(seconds) {
  seconds = Math.floor(seconds);
  let minutes = Math.floor(seconds / 60);
  seconds = seconds - minutes * 60;
  if (minutes < 10) {
    minutes = "0" + minutes;
  }
  if (seconds < 10) {
    seconds = "0" + seconds;
  }
  return minutes + ":" + seconds;
}

$.fn.player = function () {
  $(this).each(function () {
    $(this)
      .wrap('<div class="player" tabindex="1">')
      .after(
        $(
          '<div class="player__toolbar">\
          <button class="player__play"><img src="/img/play.svg" alt="play" style="width:68%"></button>\
          <div class="player__track">\
            <div class="progress bg-dark">\
              <div class="progress-bar bg-white player__progress"></div>\
            </div>\
          </div>\
          <div class="player__duration">00:00</div>\
          <a href="#" class="player__download btn" download="chenibud.mp4"><img src="/img/download.svg" alt="download" ></a>\
          <button class="player__fullscreen"><img src="/img/fullscreen_on.svg" alt="fullscreen" style="width: 70%;"></button>\
        </div>'
        )
      );

    let video = this;
    let player = $(this).closest(".player");
    let duration = player.find(".player__duration");
    let play_btn = player.find(".player__play");
    let fullscreen_btn = player.find(".player__fullscreen");
    let download_btn = player.find(".player__download");
    let track = player.find(".player__track");
    let progress_bar = player.find(".player__progress");
    let progress_bar_wrapper = progress_bar.closest(".progress");
    let popover;

    const download_url = $(video).find("source").attr("data-download-url");

    function fallback() {
      $(video).data("unsupported", true);

      let download = $(
        '\
        <div class="text-center my-5"> \
          <i class="flaticon-warning-sign" style="font-size: 5rem"></i> \
          <p>Ваш браузер не поддерживает данный формат видео</p> \
          <a class="btn btn-outline-secondary btn-sm mt-2">Скачать видео</a>\
        </div> \
      '
      );

      download.find("a").attr("href", download_url);
      player.after(download).hide();
    }

    $(video).find("source").on("error", fallback);

    video.muted = true;
    download_btn.attr("href", download_url);

    function metadata_loaded() {
      if (!video.videoWidth || !video.videoHeight) {
        fallback.call($(video).find("source")[0]);
      }
    }

    video.addEventListener("loadedmetadata", metadata_loaded);

    if (video.readyState >= 2) {
      metadata_loaded();
    }

    video.addEventListener("timeupdate", function () {
      const width = (video.currentTime / video.duration) * 100 + "%";
      progress_bar.css("width", width);
      duration.text(format_seconds(video.currentTime));
      if (popover) popover.css("left", width);
    });

    video.addEventListener("play", function () {
      play_btn.find("img").attr("src", "/img/pause.svg");
    });

    video.addEventListener("pause", function () {
      play_btn.find("img").attr("src", "/img/play.svg");
    });

    video.addEventListener("ended", function () {
      this.pause();
    });

    function seek(e) {
      let rect = progress_bar_wrapper[0].getBoundingClientRect();
      let x = e.clientX - rect.left;
      video.currentTime = (x / rect.width) * video.duration;
      progress_bar.css("width", (x / rect.width) * 100 + "%");
      if (popover)
        popover.css("left", (video.currentTime / video.duration) * 100 + "%");
    }

    progress_bar_wrapper.on("mousedown", function () {
      progress_bar_wrapper.on("mousemove", seek);
    });

    progress_bar_wrapper.on("mouseup", function () {
      progress_bar_wrapper.off("mousemove", seek);
    });

    progress_bar_wrapper.on("mouseleave", function () {
      progress_bar_wrapper.off("mousemove", seek);
    });

    progress_bar_wrapper.on("click", seek);

    fullscreen_btn.on("click", function () {
      togglePlayerFullscreen(player.get(0));
    });

    play_btn.on("click", function () {
      if (video.paused || video.ended) {
        video.play();
      } else {
        video.pause();
      }
    });

    function uncapture() {
      player.find(".player__canvas__ok").off("click");
      player.find(".player__canvas__dismiss").off("click");
      player.find(".player__canvas img").data("cropper").destroy();
      player.find(".player__backdrop").remove();
      player.find(".player__canvas").remove();
      $("body").css("overflow", "auto");
    }

    document.addEventListener("fullscreenchange", function () {
      if (player.get(0).isFullScreen) {
        $(player).find("video").removeClass("fullscreen");
        player.get(0).isFullScreen = false;
        $(player)
          .find(".player__fullscreen img")
          .attr("src", "/img/fullscreen_on.svg");
      } else {
        $(player).find("video").addClass("fullscreen");
        player.get(0).isFullScreen = true;
        $(player)
          .find(".player__fullscreen img")
          .attr("src", "/img/fullscreen_off.svg");
        if (popover) $(video).data("player").unpopover();
      }
    });
    player[0].addEventListener(
      "keydown",
      function (e) {
        if (e.keyCode === 32) {
          play_btn.trigger("click");

          e.stopPropagation();
          e.preventDefault();
        } else if (e.keyCode === 37) {
          video.pause();
          video.currentTime -= e.shiftKey ? 1 / 6 : 1 / 60;

          e.stopPropagation();
          e.preventDefault();
        } else if (e.keyCode === 39) {
          video.pause();
          video.currentTime += e.shiftKey ? 1 / 6 : 1 / 60;

          e.stopPropagation();
          e.preventDefault();
        } else if (e.keyCode === 27) {
          if (player.find(".player__canvas img").length) uncapture();
        }
      },
      false
    );

    $(video).data("player", {
      unpopover: function () {
        player.find(".player__popover__prev").off("click");
        player.find(".player__popover__next").off("click");
        player.find(".player__popover__dismiss").off("click");
        player.find(".player__popover__capture").off("click");
        popover.remove();
      },
      popover: function ({ title, ...options }, callback) {
        let unpopover = this.unpopover;

        if (popover) unpopover();

        track.append(
          '\
            <div class="player__popover">\
              <div class="player__popover__title">Создайте изображение транспорта нарушения №2</div>\
              <div class="no-wrap">\
                <div class="btn-group">\
                  <button type="button" class="player__popover__prev btn btn-secondary btn-icon"><i class="la la-angle-left"></i></button>\
                  <button type="button" class="player__popover__capture btn btn-secondary btn-icon"><i class="la la-camera"></i></button>\
                  <button type="button" class="player__popover__next btn btn-secondary btn-icon"><i class="la la-angle-right"></i></button>\
                </div>\
                <button type="button" class="player__popover__dismiss btn btn-secondary btn-icon"><i class="la la-close"></i></button>\
              </div>\
            </div>'
        );

        popover = player
          .find(".player__popover")
          .css("left", (video.currentTime / video.duration) * 100 + "%");

        player.find(".player__popover__title").text(title);

        player.find(".player__popover__prev").on("click", function () {
          video.pause();
          video.currentTime -= 1 / 60;
        });

        player.find(".player__popover__next").on("click", function () {
          video.pause();
          video.currentTime += 1 / 60;
        });

        player.find(".player__popover__dismiss").on("click", this.unpopover);
        player.find(".player__popover__capture").on("click", function () {
          video.pause();

          let canvas = document.createElement("canvas");
          canvas.height = video.videoHeight;
          canvas.width = video.videoWidth;
          canvas
            .getContext("2d")
            .drawImage(video, 0, 0, canvas.width, canvas.height);

          $("body").css("overflow", "hidden");

          player.append(
            '\
                <div class="player__backdrop"></div>\
                <div class="player__canvas">\
                  <img>\
                  <div class="player__canvas__buttons">\
                    <button class="btn btn-outline-light player__canvas__dismiss"><i class="la la-close"></i> Отменить</button>\
                    <button class="btn btn-outline-light player__canvas__ok"><i class="la la-save"></i> Сохранить</button>\
                  </div>\
                </div>'
          );

          if (KTUtil.offset(video).top < scrollTop()) {
            KTUtil.scrollTo(video, -50, 300);
          }

          player
            .find(".player__canvas img")
            .prop("src", canvas.toDataURL("image/jpeg"))
            .css({
              width: $(video).width(),
              height: $(video).height(),
            })
            .cropper({
              viewMode: 3,
              minCropBoxWidth: 30,
              minCropBoxHeight: 30,
              ...options,
            });
          player.find(".player__canvas__ok").on("click", function () {
            callback(
              player
                .find(".player__canvas img")
                .data("cropper")
                .getCroppedCanvas()
                .toDataURL("image/jpeg")
            );
            uncapture();
            unpopover();
          });

          player.find(".player__canvas__dismiss").on("click", uncapture);
        });
      },
    });
  });
};

(function (global) {
  let GHOST_ELEMENT_ID = "__autosizeInputGhost";

  let characterEntities = {
    " ": "nbsp",
    "<": "lt",
    ">": "gt",
  };

  function mapSpecialCharacterToCharacterEntity(specialCharacter) {
    return "&" + characterEntities[specialCharacter] + ";";
  }

  function escapeSpecialCharacters(string) {
    return string.replace(/\s|<|>/g, mapSpecialCharacterToCharacterEntity);
  }

  // Create `ghostElement`, with inline styles to hide it and ensure that the text is all
  // on a single line.
  function createGhostElement() {
    let ghostElement = document.createElement("div");
    ghostElement.id = GHOST_ELEMENT_ID;
    ghostElement.style.cssText =
      "display:inline-block;height:0;overflow:hidden;position:absolute;top:0;visibility:hidden;white-space:nowrap;";
    document.body.appendChild(ghostElement);
    return ghostElement;
  }

  global.autosizeInput = function (element, options) {
    // Assigns an appropriate width to the given `element` based on its contents.
    function setWidth() {
      let elementStyle = window.getComputedStyle(element);
      // prettier-ignore
      let elementCssText = 'box-sizing:' + elementStyle.boxSizing +
        ';border-left:' + elementStyle.borderLeftWidth + ' solid red' +
        ';border-right:' + elementStyle.borderRightWidth + ' solid red' +
        ';font-family:' + elementStyle.fontFamily +
        ';font-feature-settings:' + elementStyle.fontFeatureSettings +
        ';font-kerning:' + elementStyle.fontKerning +
        ';font-size:' + elementStyle.fontSize +
        ';font-stretch:' + elementStyle.fontStretch +
        ';font-style:' + elementStyle.fontStyle +
        ';font-letiant:' + elementStyle.fontVariant +
        ';font-letiant-caps:' + elementStyle.fontVariantCaps +
        ';font-letiant-ligatures:' + elementStyle.fontVariantLigatures +
        ';font-letiant-numeric:' + elementStyle.fontVariantNumeric +
        ';font-weight:' + elementStyle.fontWeight +
        ';letter-spacing:' + elementStyle.letterSpacing +
        ';margin-left:' + elementStyle.marginLeft +
        ';margin-right:' + elementStyle.marginRight +
        ';padding-left:' + elementStyle.paddingLeft +
        ';padding-right:' + elementStyle.paddingRight +
        ';text-indent:' + elementStyle.textIndent +
        ';text-transform:' + elementStyle.textTransform;

      let string = element.value || element.getAttribute("placeholder") || "";
      let ghostElement =
        document.getElementById(GHOST_ELEMENT_ID) || createGhostElement();
      ghostElement.style.cssText += elementCssText;
      ghostElement.innerHTML = escapeSpecialCharacters(string);
      let width = window.getComputedStyle(ghostElement).width;
      element.style.width = width;
      return width;
    }

    element.addEventListener("input", setWidth);

    let width = setWidth();

    if (
      options &&
      options.minWidth &&
      !element.style.minWidth &&
      width !== "0px"
    ) {
      element.style.minWidth = width;
    }

    // Return a function for unbinding the event listener and removing the `ghostElement`.
    return function () {
      element.removeEventListener("input", setWidth);
      let ghostElement = document.getElementById(GHOST_ELEMENT_ID);
      if (ghostElement) {
        ghostElement.parentNode.removeChild(ghostElement);
      }
    };
  };
})(window);

let mask_opts = {
  mask: [
    { mask: "X 000000", definitions: { X: /X/ }, cls: "vehicle_id--green" },
    { mask: "T 000000", definitions: { T: /T/ }, cls: "vehicle_id--green" },
    { mask: "D 000000", definitions: { D: /D/ }, cls: "vehicle_id--green" },
    { mask: "00 M 000000", definitions: { M: /M/ }, cls: "vehicle_id--green" },
    { mask: "00 H 000000", definitions: { H: /H/ }, cls: "vehicle_id--yellow" },
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
      mask: "00 0000 MT",
      definitions: { M: /M/, T: /T/ },
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
  ],
  prepare: function (str) {
    return str.toUpperCase();
  },
  dispatch: (appended, masked, flags) => {
    const inputValue = masked.rawInputValue;

    const inputs = masked.compiledMasks.map((m, index) => {
      m.reset();
      m.append(inputValue, { raw: true });
      m.append(appended, flags);
      return {
        index,
        weight: m.rawInputValue.length,
        current: m === masked.currentMask,
      };
    });

    inputs.sort((i1, i2) => {
      if (i2.weight === i1.weight) {
        if (i2.current) return 1;
        if (i1.current) return -1;
      }
      return i2.weight - i1.weight;
    });

    return masked.compiledMasks[inputs[0].index];
  },
};

$.fn.vehicle_id = function () {
  return $(this).each(function (i, element) {
    if (element.tagName === "INPUT") {
      let base_class = element.className;
      let mask = new IMask(element, mask_opts);

      mask.on("accept", function () {
        let mask_class = mask.masked.isComplete
          ? mask.masked.currentMask.cls
          : "";
        element.className = base_class + " " + mask_class;
      });

      mask._fireChangeEvents();
      $(element).data("imask", mask);
    } else {
      let masked = IMask.createMask(mask_opts);
      $(element).text(masked.resolve($(element).text()));
      element.className = element.className + " " + masked.currentMask.cls;
    }
  });
};

$(function () {
  $(".offense__log__btn").click(function () {
    $.get(`/staff/offenses/${$(this).data("offense-id")}/log`).done(function ({
      log,
    }) {
      $(".offense__log").html(log);
    });
  });

  $(".offense__imgs img").on("click", function () {
    $(this)
      .closest(".offense__imgs")
      .find("img")
      .gallery($(this).parent().index());
  });

  let citizenSelect = $("select[name=citizen_id]");
  citizenSelect.select2({
    ajax: {
      delay: 250,
      url: citizenSelect.data("url"),
      processResults: function (data) {
        // Tranforms the top-level key of the response object from 'items' to 'results'
        return {
          results: data.map(function ({
            first_name,
            last_name,
            id,
            phone,
            middle_name,
          }) {
            return {
              text: `${first_name || ""} ${middle_name || ""} ${
                last_name || ""
              } (${phone})`,
              id: id,
            };
          }),
        };
      },
      data: function (params) {
        // Query parameters will be ?q=[term]
        return {
          q: params.term,
        };
      },
    },
  });
  let staffSelect = $("select[name=staff_id]");
  staffSelect.select2({
    ajax: {
      delay: 250,
      url: staffSelect.data("url"),
      processResults: function (data) {
        // Tranforms the top-level key of the response object from 'items' to 'results'
        return {
          results: data.map(function ({
            first_name,
            last_name,
            id,
            phone,
            middle_name,
          }) {
            return {
              text: `${first_name || ""} ${middle_name || ""} ${
                last_name || ""
              } (${phone})`,
              id: id,
            };
          }),
        };
      },
      data: function (params) {
        // Query parameters will be ?q=[term]
        return {
          q: params.term,
        };
      },
    },
  });

  window.timePickerLocale = {
    format: "DD.MM.YYYY",
    separator: "-",
    fromLabel: "от",
    toLabel: "до",
    daysOfWeek: ["Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"],
    monthNames: [
      "Январь",
      "Февраль",
      "Март",
      "Апрель",
      "Май",
      "Июнь",
      "Июль",
      "Август",
      "Сентябрь",
      "Октябрь",
      "Ноябрь",
      "Декабрь",
    ],
    firstDay: 2,
    customRangeLabel: "Выбрать",
  };

  $("#create_time")
    .daterangepicker({
      autoApply: true,
      autoUpdateInput: false,
      locale: timePickerLocale,
      ranges: {
        Сегодня: [moment(), moment()],
        Вчера: [moment().subtract(1, "days"), moment().subtract(1, "days")],
        "Последние 3 дня": [moment().subtract(2, "days"), moment()],
        "Текущий месяц": [moment().startOf("month"), moment().endOf("month")],
        "Предыдущий месяц": [
          moment().subtract(1, "month").startOf("month"),
          moment().subtract(1, "month").endOf("month"),
        ],
      },
    })
    .on("apply.daterangepicker", function (_, picker) {
      $(this).val(
        picker.startDate.format("DD.MM.YYYY") +
          "-" +
          picker.endDate.format("DD.MM.YYYY")
      );
    })
    .on("cancel.daterangepicker", function () {
      $(this).val("");
    });
  $("#incident_time")
    .daterangepicker({
      autoApply: true,
      autoUpdateInput: false,
      locale: timePickerLocale,
      ranges: {
        Сегодня: [moment(), moment()],
        Вчера: [moment().subtract(1, "days"), moment().subtract(1, "days")],
        "Последние 3 дня": [moment().subtract(2, "days"), moment()],
        "Текущий месяц": [moment().startOf("month"), moment().endOf("month")],
        "Предыдущий месяц": [
          moment().subtract(1, "month").startOf("month"),
          moment().subtract(1, "month").endOf("month"),
        ],
      },
    })
    .on("apply.daterangepicker", function (_, picker) {
      $(this).val(
        picker.startDate.format("DD.MM.YYYY") +
          "-" +
          picker.endDate.format("DD.MM.YYYY")
      );
    })
    .on("cancel.daterangepicker", function () {
      $(this).val("");
    });

  $("select[name=area_id]").each(function () {
    let select = this;

    $(select).data("recreate", function () {
      $("select[name=district_id]")
        .find('option:not([value=""])')
        .each(function () {
          if ($(this).data("area-id") === $(select).val()) {
            $(this).show();
          } else $(this).hide();
        });
      if (
        $("select[name=district_id] option:selected").data("area-id") !==
        $(select).val()
      ) {
        $("select[name=district_id]").val("");
      }
    });
  });

  $("select[name=area_id]").on("change", function () {
    $(this).data("recreate")();
  });

  if ($("select[name=area_id]").length) {
    $("select[name=area_id]").data("recreate")();
  } else {
    $("select[name=district_id]")
      .find('option:not([value=""])')
      .each(function () {
        if (
          $(this).data("area-id") ===
          $("select[name=district_id] option:selected").data("area-id")
        ) {
          $(this).show();
        } else $(this).hide();
      });
  }

  $("span.vehicle_id").vehicle_id();

  $(".confirm-click").on("click", function (event) {
    if (!window.confirm($(this).data("confirmation") || "?")) {
      event.preventDefault();
    }
  });
});

window.post = function post(url, data, options) {
  return $.ajax({
    url: url,
    method: "post",
    headers: { "x-csrf-token": $("input[name=__anti-forgery-token]").val() },
    data: data,
    ...options,
  });
};

window.toggle_errors = function (errors) {
  Object.keys(errors).forEach(function (key) {
    let input = $(`input[name=${key}]`);
    input
      .siblings(".invalid-feedback")
      .html(errors[key] && errors[key].join(", "));
    if (errors[key]) input.addClass("is-invalid");
    else input.removeClass("is-invalid");
  });
};

window.showNotification = function (text, type) {
  $.notify(
    {
      message: text,
    },
    {
      animate: {
        enter: "animated fadeInUp",
        exit: "animated fadeOutUp",
      },
      type: type || "success",
      spacing: 10,
      offset: {
        y: 20,
      },
      allow_dismiss: false,
      newest_on_top: false,
      placement: {
        from: "bottom",
        align: "center",
      },
      delay: 1500,
    }
  );
};

$.fn.o_collapse = $.fn.collapse;

$.fn.collapse = function (show_or_hide) {
  // $(this).data('is-collapsed', show_or_hide == 'hide');
  if ($(this).hasClass("collapsing"))
    setTimeout(() => $(this).collapse(show_or_hide), 100);
  else $(this).o_collapse(show_or_hide);
};

$.fn.card = function () {
  return $(this).each(function () {
    var xhr = undefined;
    var value = undefined;

    function run(input) {
      value = input.value;

      if (xhr) xhr.abort();
      xhr = post("/verify-card", { card: value });

      xhr.then(function (data) {
        if (input.value === value) {
          if (data.error) {
            $(input).invalid(data.error);
          } else {
            $(input).invalid(false);

            $(input)
              .closest(".form-group")
              .find(".form-text")
              .text(`${data.owner} (${data.bank})`);
          }
        }
      });
    }

    $(this).each(function (_, input) {
      var mask = new IMask(input, {
        mask: "ESZZ 0000 0000 0000",
        definitions: { E: /8/, S: /6/, Z: /0/ },
      })
        .on("accept", function () {
          if (input.value !== value) {
            $(input).invalid(false);
          }
        })
        .on("complete", function () {
          run(input);
        });

      if (mask.masked.isComplete) {
        run(input);
      }
    });
  });
};

$(function () {
  $(".alert-close .close").click(function () {
    let title = $(this).data("title");
    let redirect = $(this).data("redirect");
    if (title) {
      let disabled_alerts = document.cookie
        .replace(/(?:(?:^|.*;\s*)disabled_alerts\s*\=\s*([^;]*).*$)|^.*$/, "$1")
        .split("|")
        .filter((el) => el);
      document.cookie =
        "disabled_alerts=" + [...disabled_alerts, title].join("|");
    }
    if (redirect) {
      location.href = redirect;
    }
  });
});

function debounce(func, wait, immediate) {
  var timeout;
  return function () {
    var context = this,
      args = arguments;
    var later = function () {
      timeout = null;
      if (!immediate) func.apply(context, args);
    };
    var callNow = immediate && !timeout;
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
    if (callNow) func.apply(context, args);
  };
}
