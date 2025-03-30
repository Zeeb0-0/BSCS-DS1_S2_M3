/*
  app.js - v1.3 of BSCS-DS1_S2_M3html (Enhanced with Artistic, Interactive, and Export Modal Features)
  Displays a scatter chart with trend lines for average stress per sleep hour bin,
  grouped by Grade, Gender, or Department.
  
  CSV columns expected:
    Sleep_Hours_per_Night
    Stress_Level (1-10)
    Grade
    Gender
    Department
*/

// Global chart reference, raw data, and animation toggle flag
let scatterChart = null;
let rawData = [];
let animationsEnabled = true; // default ON

// DOM elements
const csvFileInput = document.getElementById('csvFile');
const resetBtn = document.getElementById('resetBtn');
const groupSelect = document.getElementById('groupSelect');
const messageArea = document.getElementById('messageArea');
const toggleAnimationsBtn = document.getElementById('toggleAnimationsBtn');
const exportBtn = document.getElementById('exportBtn');

// Export modal elements
const exportModal = document.getElementById('exportModal');
const confirmExportBtn = document.getElementById('confirmExportBtn');
const cancelExportBtn = document.getElementById('cancelExportBtn');
const exportFilenameInput = document.getElementById('exportFilename');

// Set bin size (in hours)
const BIN_SIZE = 0.5;

// Attach event listeners on DOM load
document.addEventListener('DOMContentLoaded', () => {
  csvFileInput.addEventListener('change', handleFile);
  resetBtn.addEventListener('click', resetChart);
  groupSelect.addEventListener('change', () => {
    if (rawData.length > 0) {
      renderChart(groupSelect.value);
    }
  });
  toggleAnimationsBtn.addEventListener('click', toggleAnimations);
  exportBtn.addEventListener('click', showExportModal);
  confirmExportBtn.addEventListener('click', handleExport);
  cancelExportBtn.addEventListener('click', hideExportModal);
  updateToggleButton(); // Initialize toggle button style and text
});

/**
 * Toggles animations on/off and updates the chart options if rendered.
 */
function toggleAnimations() {
  animationsEnabled = !animationsEnabled;
  updateToggleButton();
  showMessage(`Animations ${animationsEnabled ? "enabled" : "disabled"}.`, "info");
  if (scatterChart) {
    scatterChart.options.animation = animationsEnabled ? { duration: 1000, easing: 'easeOutQuart' } : false;
    scatterChart.update();
  }
}

/**
 * Updates the toggle animations button's appearance.
 */
function updateToggleButton() {
  if (animationsEnabled) {
    toggleAnimationsBtn.classList.remove('off');
    toggleAnimationsBtn.classList.add('on');
    toggleAnimationsBtn.textContent = "Animations: ON";
  } else {
    toggleAnimationsBtn.classList.remove('on');
    toggleAnimationsBtn.classList.add('off');
    toggleAnimationsBtn.textContent = "Animations: OFF";
  }
}

/**
 * Returns a color based on the group attribute and value.
 */
function getColor(groupBy, value) {
  if (groupBy === "Grade") {
    const gradeColors = { "F": "#e74c3c", "D": "#e67e22", "C": "#f1c40f", "B": "#27ae60", "A": "#2ecc71" };
    return gradeColors[value] || "#9b59b6";
  } else if (groupBy === "Gender") {
    const genderColors = { "Female": "#e74c3c", "Male": "#3498db" };
    return genderColors[value] || "#9b59b6";
  } else if (groupBy === "Department") {
    const deptColors = { "Engineering": "#2ecc71", "Business": "#3498db", "CS": "#e74c3c", "Mathematics": "#f1c40f" };
    return deptColors[value] || "#e67e22";
  }
  return "#95a5a6";
}

/**
 * Displays a feedback message with color based on type.
 */
function showMessage(msg, type = 'error') {
  messageArea.textContent = msg;
  messageArea.style.color = type === 'error' ? '#e74c3c' : '#27ae60';
}

/**
 * Handles CSV file input using PapaParse.
 */
function handleFile(event) {
  const file = event.target.files[0];
  if (!file) {
    showMessage("No file selected.");
    return;
  }
  if (!file.name.toLowerCase().endsWith('.csv')) {
    showMessage("Please upload a valid CSV file (.csv).");
    return;
  }
  showMessage("", 'info');
  Papa.parse(file, {
    header: true,
    skipEmptyLines: true,
    complete: (results) => {
      if (results.errors.length > 0) {
        showMessage(`CSV parse error: ${results.errors[0].message}`);
        return;
      }
      processCSV(results.data);
    },
    error: (err) => {
      showMessage(`Error reading file: ${err.message}`);
    }
  });
}

/**
 * Validates CSV columns and stores raw data.
 */
function processCSV(data) {
  const requiredCols = ["Sleep_Hours_per_Night", "Stress_Level (1-10)", "Grade", "Gender", "Department"];
  for (let col of requiredCols) {
    if (!data[0].hasOwnProperty(col)) {
      showMessage(`CSV missing column: ${col}`);
      return;
    }
  }
  rawData = data.filter((row, idx) => {
    const sleep = parseFloat(row["Sleep_Hours_per_Night"]);
    const stress = parseFloat(row["Stress_Level (1-10)"]);
    if (isNaN(sleep) || isNaN(stress)) {
      console.warn(`Skipping row ${idx + 2} due to invalid numeric data.`);
      return false;
    }
    return true;
  });
  if (rawData.length === 0) {
    showMessage("No valid data found in CSV.");
    return;
  }
  showMessage("CSV parsed successfully.", "info");
  renderChart(groupSelect.value);
}

/**
 * Bins data by Sleep_Hours_per_Night and computes average values.
 */
function computeBinnedAverages(groupData, binSize = BIN_SIZE) {
  const bins = {};
  groupData.forEach(row => {
    const sleep = parseFloat(row["Sleep_Hours_per_Night"]);
    const stress = parseFloat(row["Stress_Level (1-10)"]);
    const binIndex = Math.floor(sleep / binSize);
    if (!bins[binIndex]) {
      bins[binIndex] = { sumSleep: 0, sumStress: 0, count: 0 };
    }
    bins[binIndex].sumSleep += sleep;
    bins[binIndex].sumStress += stress;
    bins[binIndex].count += 1;
  });
  const points = [];
  Object.keys(bins).forEach(binIdx => {
    const info = bins[binIdx];
    points.push({
      x: info.sumSleep / info.count,
      y: info.sumStress / info.count
    });
  });
  return points.sort((a, b) => a.x - b.x);
}

/**
 * Renders the scatter chart based on the selected group.
 */
function renderChart(groupBy) {
  const ctx = document.getElementById('scatterChart').getContext('2d');
  if (scatterChart) {
    scatterChart.destroy();
  }
  const groups = {};
  rawData.forEach(row => {
    const key = row[groupBy];
    if (!groups[key]) {
      groups[key] = [];
    }
    groups[key].push(row);
  });
  const datasets = Object.keys(groups).map((groupVal) => {
    const binnedData = computeBinnedAverages(groups[groupVal]);
    return {
      label: `${groupBy}: ${groupVal}`,
      data: binnedData,
      backgroundColor: getColor(groupBy, groupVal),
      borderColor: getColor(groupBy, groupVal),
      pointStyle: 'rect',
      pointRadius: 5,
      pointHoverRadius: 5,
      showLine: true,
      fill: false,
      tension: 0
    };
  });
  const chartAnimations = animationsEnabled ? { duration: 1000, easing: 'easeOutQuart' } : false;
  const config = {
    type: 'scatter',
    data: { datasets },
    options: {
      animation: chartAnimations,
      responsive: true,
      plugins: {
        legend: { position: 'top' },
        tooltip: {
          callbacks: {
            label: (context) => {
              const { x, y } = context.raw;
              return `${groupBy}: ${context.dataset.label.split(': ')[1]} | Avg Sleep: ${x.toFixed(2)} | Avg Stress: ${y.toFixed(2)}`;
            }
          }
        },
        zoom: {
          pan: { enabled: true, mode: 'xy' },
          zoom: { enabled: true, mode: 'xy' }
        }
      },
      scales: {
        x: {
          type: 'linear',
          title: { display: true, text: 'Average Sleep Hours' },
          grid: { color: 'rgba(0,0,0,0.2)' },
          min: 0,
          max: 10
        },
        y: {
          title: { display: true, text: 'Average Stress (1-10)' },
          grid: { color: 'rgba(0,0,0,0.2)' },
          min: 0,
          max: 10
        }
      }
    }
  };
  scatterChart = new Chart(ctx, config);
  const canvas = document.getElementById('scatterChart');
  if (animationsEnabled) {
    canvas.style.opacity = 0;
    setTimeout(() => {
      canvas.style.transition = 'opacity 1s ease-out';
      canvas.style.opacity = 1;
    }, 50);
  } else {
    canvas.style.transition = '';
    canvas.style.opacity = 1;
  }
}

/**
 * Resets the chart and clears the CSV input.
 */
function resetChart() {
  if (scatterChart) {
    scatterChart.destroy();
    scatterChart = null;
  }
  rawData = [];
  csvFileInput.value = '';
  showMessage("Chart has been reset.", "info");
}

/**
 * Shows the export modal.
 */
function showExportModal() {
  exportModal.style.display = "block";
}

/**
 * Hides the export modal.
 */
function hideExportModal() {
  exportModal.style.display = "none";
}

/**
 * Handles the export modal's "Export" button.
 */
function handleExport() {
  const selectedType = document.querySelector('input[name="exportType"]:checked').value;
  const filename = exportFilenameInput.value.trim() || "chart_output";
  hideExportModal();
  if (selectedType === "csv") {
    exportCSV(filename);
  } else if (selectedType === "png" || selectedType === "jpg") {
    exportChartAsImage(filename, selectedType);
  } else {
    showMessage("Invalid export file type selected.", "error");
  }
}

/**
 * Exports the computed chart data as a human-readable CSV file.
 */
function exportCSV(filename) {
  if (!scatterChart) {
    showMessage("No chart data to export.", "error");
    return;
  }
  let csvContent = "=== Chart Export ===\n\n";
  scatterChart.data.datasets.forEach(dataset => {
    // Create header for each dataset
    const groupLabel = dataset.label.split(': ')[1];
    csvContent += `Dataset: ${groupLabel}\n`;
    csvContent += "Avg Sleep,Avg Stress\n";
    dataset.data.forEach(point => {
      // Pad columns for better alignment (using fixed widths)
      const sleepStr = point.x.toFixed(2).padStart(8, ' ');
      const stressStr = point.y.toFixed(2).padStart(8, ' ');
      csvContent += `${sleepStr},${stressStr}\n`;
    });
    csvContent += "\n";
  });
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", `${filename}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  showMessage("CSV exported successfully.", "info");
}

/**
 * Exports the chart canvas as an image file.
 */
function exportChartAsImage(filename, type) {
  if (!scatterChart) {
    showMessage("No chart available for export.", "error");
    return;
  }
  const canvas = document.getElementById('scatterChart');
  const dataURL = canvas.toDataURL(`image/${type}`);
  const link = document.createElement("a");
  link.href = dataURL;
  link.download = `${filename}.${type}`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  showMessage(`Chart exported as image (${type.toUpperCase()}).`, "info");
}

// Close export modal when clicking outside modal content
window.addEventListener("click", (event) => {
  if (event.target === exportModal) {
    hideExportModal();
  }
});
