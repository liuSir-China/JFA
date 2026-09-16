(function () {
  var API = "/jfa/api";
  var grid = document.getElementById("grid");
  var counts = document.getElementById("counts");
  var modal = document.getElementById("modal");
  var modalBody = document.getElementById("modal-body");
  var pollTimer = null;

  function getJson(url, cb) {
    var xhr = new XMLHttpRequest();
    xhr.open("GET", url, true);
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) {
        return;
      }
      var data = parse(xhr.responseText);
      cb(xhr.status, data);
    };
    xhr.send(null);
  }

  function postJson(url, cb) {
    var xhr = new XMLHttpRequest();
    xhr.open("POST", url, true);
    xhr.setRequestHeader("Content-Type", "application/json; charset=UTF-8");
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) {
        return;
      }
      var data = parse(xhr.responseText);
      cb(xhr.status, data);
    };
    xhr.send("{}");
  }

  function parse(text) {
    if (!text) {
      return {};
    }
    try {
      return JSON.parse(text);
    } catch (e) {
      return { message: text };
    }
  }

  function setCounts(a, u) {
    counts.textContent = "已分析 " + a + " · 未分析 " + u;
  }

  function clearNode(node) {
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  function render(data) {
    var services = data.services || [];
    setCounts(data.analyzed || 0, data.unanalyzed || 0);
    clearNode(grid);
    var anyAnalyzing = false;
    var i;
    for (i = 0; i < services.length; i++) {
      grid.appendChild(cardEl(services[i]));
      if (services[i].analyzing) {
        anyAnalyzing = true;
      }
    }
    togglePoll(anyAnalyzing);
  }

  function cardEl(svc) {
    var card = document.createElement("div");
    var analyzed = !!svc.analyzed;
    var analyzing = !!svc.analyzing;
    card.className = "card " + (analyzed ? "blue" : "gray") + (analyzing ? " analyzing" : "");
    card.setAttribute("data-pid", String(svc.pid));

    var pidLine = document.createElement("div");
    pidLine.className = "card-pid";
    pidLine.appendChild(document.createTextNode("PID"));
    var strong = document.createElement("strong");
    strong.appendChild(document.createTextNode(String(svc.pid)));
    pidLine.appendChild(strong);

    var main = document.createElement("div");
    main.className = "card-main";
    main.appendChild(document.createTextNode(svc.main || ("pid " + svc.pid)));

    var user = document.createElement("div");
    user.className = "card-user";
    user.appendChild(document.createTextNode(svc.user ? String(svc.user) : ""));

    card.appendChild(pidLine);
    card.appendChild(main);
    if (svc.user) {
      card.appendChild(user);
    }
    if (analyzing) {
      var overlay = document.createElement("div");
      overlay.className = "overlay";
      var spin = document.createElement("div");
      spin.className = "spinner";
      overlay.appendChild(spin);
      card.appendChild(overlay);
    } else {
      card.onclick = function () {
        onCard(svc);
      };
    }
    return card;
  }

  function onCard(svc) {
    if (svc.analyzing) {
      return;
    }
    if (svc.analyzed) {
      openReport(svc.pid);
      return;
    }
    if (window.confirm("首次分析此项目，是否开始分析")) {
      startAnalyze(svc.pid);
    }
  }

  function openReport(pid) {
    getJson(API + "/services/" + pid + "/report", function (status, data) {
      if (status < 200 || status >= 300) {
        window.alert(data.message || ("HTTP " + status));
        return;
      }
      modalBody.textContent = data.text || "";
      modal.className = "modal";
    });
  }

  function startAnalyze(pid) {
    postJson(API + "/services/" + pid + "/analyze", function (status, data) {
      if (status < 200 || status >= 300) {
        window.alert(data.message || ("HTTP " + status));
        return;
      }
      load();
    });
  }

  function closeModal() {
    modal.className = "modal hidden";
    modalBody.textContent = "";
  }

  function togglePoll(needed) {
    if (needed) {
      if (!pollTimer) {
        pollTimer = setInterval(load, 1000);
      }
    } else if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  function load() {
    getJson(API + "/services", function (status, data) {
      if (status < 200 || status >= 300) {
        return;
      }
      render(data);
    });
  }

  document.getElementById("modal-close").onclick = closeModal;
  document.getElementById("modal-backdrop").onclick = closeModal;
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" || e.keyCode === 27) {
      closeModal();
    }
  });

  load();
})();
