function sprintf() {
  var args = arguments,
    string = args[0],
    i = 1;
  return string.replace(/%((%)|s|d)/g, function (m) {
    var val = null;
    if (m[2]) {
      val = m[2];
    } else {
      val = args[i];
      switch (m) {
        case '%d':
          val = parseFloat(val);
          if (isNaN(val)) {
            val = 0;
          }
          break;
      }
      i++;
    }
    return val;
  });
}

var locale = "{{locale|name}}"

var translations = {{translations|default:"{}"|safe}};
window.t = function (str) {
  {% if translations %}
  if (!translations[str]) {
    console.warn('Translation is missing ' + str);
    {% if make-request %}
    $.get("/missing-translation", {text: str})
    {% endif %}
  }
  {% endif %}

  var args = [].slice.call(arguments).slice(1);
  args.unshift(translations[str]||str);
  return sprintf.apply(null,args);
};
