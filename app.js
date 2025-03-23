/*
  app.js - Single scatter chart showing trend lines for average stress per sleep hour bin,
  computed per group (selected by user: Grade, Gender, or Department).

  For each group, we:
    - Bin data by Sleep_Hours_per_Night (using a bin size, here default is 0.5 hours).
    - Compute the average Sleep and average Stress in that bin.
    - Plot these averages as points, and connect them with a line.

  This yields a trend line for each group, showing how average stress changes as sleep hours vary.
  
  CSV columns expected:
    Sleep_Hours_per_Night
    Stress_Level (1-10)
    Grade
    Gender
    Department
*/

// Global chart reference and raw data
let scatterChart = null;
let rawData = [];

// DOM elements
const csvFileInput = document.getElementById('csvFile');
const resetBtn = document.getElementById('resetBtn');
const groupSelect = document.getElementById('groupSelect');
const messageArea = document.getElementById('messageArea');

// Set bin size (in hours) for grouping sleep data
const BIN_SIZE = 0.5;

// On DOM load, attach event listeners
document.addEventListener('DOMContentLoaded', () => {
  csvFileInput.addEventListener('change', handleFile);
  resetBtn.addEventListener('click', resetChart);
  groupSelect.addEventListener('change', () => {
    if (rawData.length > 0) {
      renderChart(groupSelect.value);
    }
  });
});

/**
 * Returns a color based on the grouping attribute and its value.
 * @param {string} groupBy - "Grade", "Gender", or "Department"
 * @param {string} value - the group value.
 */
function getColor(groupBy, value) {
  if (groupBy === "Grade") {
    // For Grade: F = red, D = orange, C = yellow, B = light green, A = green
    const gradeColors = { "F": "#e74c3c", "D": "#e67e22", "C": "#f1c40f", "B": "#27ae60", "A": "#2ecc71" };
    return gradeColors[value] || "#9b59b6"; // fallback color
  } else if (groupBy === "Gender") {
    // For Gender: Female = red, Male = blue
    const genderColors = { "Female": "#e74c3c", "Male": "#3498db" };
    return genderColors[value] || "#9b59b6";
  } else if (groupBy === "Department") {
    // Example department colors; adjust as needed.
    const deptColors = { "Engineering": "#2ecc71", "Business": "#3498db", "CS": "#e74c3c", "Mathematics": "#f1c40f" };
    return deptColors[value] || "#e67e22";
  }
  return "#95a5a6"; // default
}

/**
 * Display a feedback message.
 * @param {string} msg - The message.
 * @param {string} type - 'error' or 'info' (default: error).
 */
function showMessage(msg, type = 'error') {
  messageArea.textContent = msg;
  messageArea.style.color = type === 'error' ? '#e74c3c' : '#2ecc71';
}

/**
 * Handle CSV file input using PapaParse.
 * @param {Event} event - File input change event.
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
 * Validate CSV columns and store raw data.
 * @param {Array} data - Parsed CSV data.
 */
function processCSV(data) {
  const requiredCols = ["Sleep_Hours_per_Night", "Stress_Level (1-10)", "Grade", "Gender", "Department"];
  for (let col of requiredCols) {
    if (!data[0].hasOwnProperty(col)) {
      showMessage(`CSV missing column: ${col}`);
      return;
    }
  }

  // Filter rows with valid numeric values
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
 * Bin data by Sleep_Hours_per_Night and compute averages for a given group.
 * @param {Array} groupData - Array of rows for one group.
 * @param {number} binSize - Size of each bin (default BIN_SIZE).
 * @returns {Array} - Array of points { x: avgSleep, y: avgStress } for each bin.
 */
function computeBinnedAverages(groupData, binSize = BIN_SIZE) {
  const bins = {}; // { binIndex: { sumSleep, sumStress, count } }
  groupData.forEach(row => {
    const sleep = parseFloat(row["Sleep_Hours_per_Night"]);
    const stress = parseFloat(row["Stress_Level (1-10)"]);
    // Determine bin index: using floor(sleep / binSize)
    const binIndex = Math.floor(sleep / binSize);
    if (!bins[binIndex]) {
      bins[binIndex] = { sumSleep: 0, sumStress: 0, count: 0 };
    }
    bins[binIndex].sumSleep += sleep;
    bins[binIndex].sumStress += stress;
    bins[binIndex].count += 1;
  });

  // Convert bins into an array of points
  const points = [];
  Object.keys(bins).forEach(binIdx => {
    const info = bins[binIdx];
    points.push({
      x: info.sumSleep / info.count,
      y: info.sumStress / info.count
    });
  });
  // Sort points by x value
  return points.sort((a, b) => a.x - b.x);
}

/**
 * Render scatter chart based on selected grouping attribute.
 * For each group value, bin the data by sleep hours and compute average values.
 * Each group is rendered as a separate dataset (with line and points) to show trends.
 * @param {string} groupBy - "Grade" | "Gender" | "Department"
 */
function renderChart(groupBy) {
  const ctx = document.getElementById('scatterChart').getContext('2d');
  if (scatterChart) {
    scatterChart.destroy();
  }

  // Group rawData by the selected attribute.
  const groups = {};
  rawData.forEach(row => {
    const key = row[groupBy];
    if (!groups[key]) {
      groups[key] = [];
    }
    groups[key].push(row);
  });

  // Create a dataset for each group using binned averages.
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
      tension: 0  // Straight lines
    };
  });

  const config = {
    type: 'scatter',
    data: { datasets },
    options: {
      animation: false,
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
}

/**
 * Reset chart and clear file input.
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
