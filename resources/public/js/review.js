function init_map() {
  if ($("#map").length) {
    var center = $("#map").data("center").split(",");

    var map = new ymaps.Map("map", {
      center: center,
      zoom: 14,
      controls: ["typeSelector"],
    });

    var marker = new ymaps.GeoObject({
      geometry: { type: "Point", coordinates: center },
    });

    map.geoObjects.add(marker);
  }
}

function convertImage(file, handler) {
  let reader = new FileReader();
  reader.onload = function () {
    let image = new Image();
    image.onload = function () {
      let canvas = document.createElement("canvas");
      canvas.height = image.height;
      canvas.width = image.width;
      let context = canvas.getContext("2d");
      context.fillStyle = "white";
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      handler(canvas.toDataURL("image/jpeg"));
    };
    image.src = reader.result;
  };
  reader.readAsDataURL(file);
}

function chooseImage(handler) {
  $('<input type=file accept="image/*">')
    .appendTo($("body"))
    .hide()
    .on("change", function (e) {
      if (e.target.files.length !== 0) {
        handler(e.target.files[0]);
      }
    })
    .trigger("click");
}

$(function () {
  const report_id = $("[data-report-id]").data("report-id");

  $("video").player();

  let tab = "video";
  $('#video-content a[data-toggle="tab"]').on("shown.bs.tab", function (e) {
    tab = $(e.target).data("tab");
  });

  function changeOffenseImg(src) {
    $(this)
      .find(".offense__img__input")
      .val(src)
      .end()
      .find(".btn-group")
      .find(".offense__img__prev")
      .remove()
      .end()
      .prepend(
        $(
          '<button type="button" class="btn btn-secondary p-0 offense__img__prev"><img></button>'
        )
          .find("img")
          .prop("src", src)
          .end()
      )
      .find("i.la-image")
      .remove();
  }

  $(document).on("click", ".offense__img__file", function () {
    let container = $(this).closest(".offense__img");
    chooseImage(function (image) {
      convertImage(image, changeOffenseImg.bind(container[0]));
    });
  });

  $(document).on("click", ".offense__img__crop", function () {
    let container = $(this).closest(".offense__img");
    let video = $("#nav-" + tab + " video");
    if (video.data("unsupported")) {
      container.find(".offense__img__file").trigger("click");
    } else {
      let scrollPos = scrollTop();
      $("html, body").animate(
        { scrollTop: $("#nav-" + tab + " .player").offset().top + 60 },
        300
      );
      $("#nav-" + tab + " video")
        .data("player")
        .popover(
          {
            title: $(this).attr("title"),
            aspectRatio: $(this).data("ratio") || 0,
            autoCropArea: $(this).data("area") || 0.5,
          },
          function (src) {
            changeOffenseImg.call(container[0], src);
            $("html, body").animate({ scrollTop: scrollPos }, 300);
          }
        );
    }
  });

  $(document).on("click", ".offense__img__prev", function () {
    $(this)
      .closest(".form-group")
      .find(".offense__img__prev img")
      .gallery($(this).closest(".offense__img").index());
  });

  $(document).on("change", "input[name*=status]", function () {
    if (this.value === "accepted") {
      $(this)
        .closest(".offense")
        .find(".fields--rejected")
        .slideUp()
        .find("select[name], input[name], textarea[name]")
        .prop("required", false);
      $(this)
        .closest(".offense")
        .find(".fields--accepted")
        .slideDown()
        .find(
          "select[name]:not([data-optional]), input[name]:not([data-optional]), textarea[name]:not([data-optional])"
        )
        .prop("required", true);
    } else {
      $(this)
        .closest(".offense")
        .find(".fields--accepted")
        .slideUp()
        .find("select[name], input[name], textarea[name]")
        .prop("required", false);
      $(this)
        .closest(".offense")
        .find(".fields--rejected")
        .slideDown()
        .find(
          "select[name]:not([data-optional]), input[name]:not([data-optional]), textarea[name]:not([data-optional])"
        )
        .prop("required", true);
    }
  });

  $(document).on("change", ".response-templates", function () {
    $(event.target)
      .closest(".offense")
      .find("textarea[name*=response]")
      .val(
        $(event.target)
          .find("option:selected")
          .text()
          .replace(/^\d+\. /, "")
      );
  });

  function openArticleSelector(selectedId, onSelect) {
    let modal = $(".article-select-modal");
    $(".article-option").removeClass("selected");
    selectedId &&
      $(".article-option[data-id=" + selectedId + "]").addClass("selected");
    modal.modal("show");
    let option = modal.find(".article-option");
    option.off();
    option.on("click", function () {
      const selected = $(this);
      onSelect({
        id: selected.data("id"),
        number: selected.data("number"),
        factor: selected.data("factor"),
        alias: selected.data("alias"),
        text: selected.data("text"),
      });
      modal.modal("hide");
    });
  }

  $("form.offenses").on("submit", function (e) {
    let hasAcceptedOffenses = false;
    $(".vehicle-modal .vehicles.row").empty();
    $(this)
      .find(".offense:visible")
      .each(function () {
        let src =
          $(this).find("img.vehicle_id_img").attr("src") ||
          $(this).find("input.vehicle_id_img").val();
        let vehicleId = $(this).find("input.vehicle_id").val();
        let title = $(this).find(".kt-portlet__head-title").html();
        if (src && $(this).find("input[value=accepted]").is(":checked")) {
          hasAcceptedOffenses = true;
          $(".vehicle-modal .vehicles.row").append(
            $(`
        <div class="col-sm-12 col-md-6 col-lg-4">
            <div class="text-center mx-3 my-4 p-2 shadow">
            <div class="p-2 kt-label-font-color-3">${title}</div>
              <button type="button" class="btn btn-secondary p-0 img__prev vehicle_id__prev">
                <img style="width: 100%" src="${src}" alt="">
              </button>
              <span class="vehicle_id vehicle_id-sm vehicle_id mt-3">${vehicleId}</span>
            </div>
        </div>`)
          );
          $(".img__prev").on("click", function () {
            $(this).find("img").gallery(0);
            // to fix modal bug
            $(".vehicle-modal").on("hidden.bs.modal", () =>
              $("#pswp").attr("class", "pswp")
            );
          });
        }
      });
    $(this).find(".vehicle_id").vehicle_id();
    if (hasAcceptedOffenses) {
      $(".vehicle-modal").modal("show");
      e.preventDefault();
      $(this)
        .find(".confirm-btn")
        .on("click", function () {
          $(this).closest("form").off("submit");
        });
    }
  });

  function initArticleSelect(wrapper) {
    $(wrapper)
      .find(".article-select-btn")
      .on("click", function () {
        let btn = this;
        let input = $(btn).siblings(".article_id_input");
        openArticleSelector(
          input.val(),
          function ({ id, number, factor, alias, text }) {
            input.val(id);
            let option = `<div class="article-option article-select-btn">
          <div class="d-flex">
            <div class="text-right no-wrap mr-3">
              <div class="kt-font-brand mb-1" style="font-size: 1.25em; line-height: 1">${number}</div>
              <div class="kt-label-font-color-1" style="line-height: 1">
                ${factor} <span style="font-size: 0.75em"> ${t("МРЗП")}</span>
              </div>
            </div>
            <div class="kt-label-font-color-3" style="line-height: 1.28">
              ${alias}
              <div class="mt-1">${text}</div>
            </div>
          </div>
        </div>`;
            $(btn).replaceWith($(option));
            initArticleSelect(wrapper);
          }
        );
      });
  }

  function init_offense(wrapper) {
    $(wrapper)
      .find("input.vehicle_id")
      .attr("class", "vehicle_id")
      .vehicle_id()
      .each(function () {
        autosizeInput(this);
      })
      .each(function () {
        const link = $(this).siblings(".vehicle-offenses-link");
        const span = $(this).siblings(".vehicle-offenses-zero");
        const mask = $(this).data("imask");
        const checkbox = $(this)
          .closest(".offense")
          .find("input[name*=not_duplicate]");
        const refresh = debounce(
          function () {
            const vehicle_id = mask.unmaskedValue;
            $.get("/staff/reports/vehicle", {
              vehicle_id,
              exclude_report_id: report_id,
            }).done(function ({ count }) {
              if (vehicle_id === mask.unmaskedValue) {
                if (count) {
                  span.hide();
                  link
                    .show()
                    .attr(
                      "href",
                      link
                        .attr("href")
                        .replace(/vehicle_id=.*$/, "vehicle_id=" + vehicle_id)
                    )
                    .text(link.text().replace(/\d+/, count));
                  checkbox
                    .prop("required", true)
                    .removeAttr("data-optional")
                    .closest(".form-group")
                    .slideDown();
                } else {
                  link.hide();
                  span.show();
                  checkbox
                    .prop("required", false)
                    .attr("data-optional", "true")
                    .closest(".form-group")
                    .slideUp();
                }
              }
            });
          },
          1000,
          true
        );

        mask.on("accept", function () {
          if (mask.masked.isComplete) {
            refresh();
          } else {
            link.hide();
            span.hide();
          }
        });
      });
    $(wrapper)
      .find(".offense")
      .each(function () {
        const offenseWrapper = $(this);
        const applyToAllButton = offenseWrapper.find(
          ".offense__apply_all__btn"
        );
        const responseSelect = offenseWrapper.find(".response-select");
        applyToAllButton.toggle(responseSelect.val());
        offenseWrapper.find(".response-select").on("change", function (e) {
          applyToAllButton.show();
        });
      });
    $(wrapper)
      .find(".offense__apply_all__btn")
      .on("click", function (e) {
        const offense_id = $(this).data("offense-id");

        const inputs = $(
          offense_id
            ? `.offense:not(#${offense_id}) input[name*=status][value=rejected]`
            : `input[name*=status][value=rejected]`
        );
        inputs.prop("checked", true);
        inputs.change();

        const selectedResponse = $(
          `.offense#${offense_id} .response-select`
        ).val();

        $(`.offense:not(#${offense_id}) .response-select`)
          .val(selectedResponse)
          .change();

        const with_text = $(
          `.offense#${offense_id} .offense__response-toggler`
        ).prop("checked");

        $(`.offense:not(#${offense_id}) .offense__response-toggler`)
          .prop("checked", with_text)
          .change();
        const response_text = $(
          `.offense#${offense_id} [name*=extra_response]`
        ).val();
        $(`.offense:not(#${offense_id}) [name*=extra_response]`)
          .val(response_text)
          .change();
      });

    $(wrapper)
      .find(".offense__response-toggler")
      .on("change", function (event) {
        if (event.target.checked) {
          $(event.target).closest(".form-group").find("textarea").slideDown();
        } else {
          $(event.target)
            .closest(".form-group")
            .find("textarea")
            .slideUp()
            .val("");
        }
      });

    $(wrapper)
      .find("select.article-select")
      .select2({
        placeholder: $(wrapper)
          .find("select.article-select")
          .data("placeholder"),
        escapeMarkup: function (markup) {
          return markup;
        },
        templateResult: function (data) {
          return `<div style="margin: -5px -15px; padding: 5px 15px" class="select2-results__option--factor-${$(
            data.element
          ).data("factor")}">${data.text}</div>`;
        },
      });
    $(wrapper)
      .find("select.response-select")
      .select2({
        placeholder: $(wrapper)
          .find("select.response-select")
          .data("placeholder"),
      });
    $(wrapper)
      .find(".offense")
      .each(function () {
        initArticleSelect(this);
      });
  }

  $(".offenses").repeater({
    hide: function (deleteElement) {
      $(this).slideUp(deleteElement);
    },
    show: function () {
      $(this).find("[disabled]").removeAttr("disabled");
      $(this).find("select.article-select").val("");
      $(this).find("select.response-select").val("");
      init_offense(this);
      $(this).slideDown();
      $(this).find(".offense__img__prev").remove();
      $(this)
        .find(".offense__img__crop")
        .find("i.la-image")
        .remove()
        .end()
        .prepend('<i class="la la-image"/>');
      $(this).find(".testimony").closest(".form-group").remove();
      $(this)
        .find("input[name*=status][value=accepted]")
        .prop("checked", true)
        .trigger("change");
      $(this).find("input[name*=status]:not(:checked)").prop("disabled", true);
      $(this)
        .find(".kt-portlet__head-title")
        .text($(".offenses").data("new-offense-title"))
        .append(
          $("<small>").text($(".offenses").data("new-offense-sub-title"))
        );
      $(this).find(".kt-portlet__head").append(
        `<div class="kt-portlet__head-toolbar">
            <div class="kt-portlet__head-actions">
              <button data-repeater-delete type="button" class="btn btn-clean btn-sm btn-icon btn-icon-md">
                <i class="la la-remove"></i>
              </button>
            </div>
          </div>`
      );
      $(this).find(".vehicle-offenses-link").hide();
      $(this)
        .find(".article-select-btn")
        .replaceWith(
          $(`<button type="button" class="btn btn-sm btn-outline-secondary article-select-btn">
             ${t("Выберите статью")}
           </button>`)
        );
      initArticleSelect(this);
    },
  });

  init_offense(document);

  $("input[name*=status]:checked").each(function () {
    $(this).trigger("change");
  });
});
