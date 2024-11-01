import v4 from "/npm/uuid/dist/esm-browser/v4.js";

$(function () {
  // $("[maxlength]").maxlength();
  function onGrantSelect(selectedGrantType) {
    $("[data-grant-type]")
      .hide()
      .find("input, textarea, button, select")
      .prop("disabled", true);
    if (selectedGrantType) {
      $(`[data-grant-type=${selectedGrantType}]`)
        .show()
        .find("input, textarea, button, select")
        .prop("disabled", false);
    }
  }

  function joinInputVals(inputs) {
    return $.map(inputs, (i) => $(i).val()).join(",");
  }

  if (!$("[name=id]").val()) {
    $("[name=id]").val(v4());
  }

  $("#client_id__refresh").on("click", function () {
    $("[name=id]").val(v4());
  });

  $('[data-toggle="tooltip"]').tooltip({ delay: 350 });

  $("#client_form").submit(function () {
    $("input[name=redirect_uri]").val(joinInputVals($("#redirect_uris input")));

    $("input[name=allowed_scope]").val(
      joinInputVals($("#allowed_scope input:checked"))
    );

    $("input[name=default_scope]").val(
      joinInputVals($("#default_scope input:checked"))
    );

    return true;
  });
  var grantSelect = $("[name=grant_type]");
  onGrantSelect(grantSelect.val());
  grantSelect.on("click", function () {
    var selectedGrantType = $(this).val();
    onGrantSelect(selectedGrantType);
  });

  $("#redirect_uris").repeater({
    initEmpty: false,
    show: function (input) {
      $(this).find("[data-repeater-create]").remove();
      $(this).find("input").val("").prop("disabled", false);
      $(this).find("label").remove();
      $(this).slideDown();
    },
    hide: function (deleteElement) {
      $(this).slideUp(deleteElement);
    },
    isFirstItemUndeletable: true,
  });
});
