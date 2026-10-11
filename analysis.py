"""
Google Sheets Data Analysis
============================
Run:  python analysis.py
Requires: pandas, numpy, scipy, matplotlib, seaborn
Install:  pip install pandas numpy scipy matplotlib seaborn
"""

import pandas as pd
import numpy as np
from scipy import stats
import matplotlib.pyplot as plt
import seaborn as sns


# ===============================================================
# 1. LOAD DATA
# ===============================================================
df = pd.read_csv("participant_data.csv")

print("=" * 60)
print("1. DATA OVERVIEW")
print("=" * 60)
print(f"Rows: {df.shape[0]}")
print(f"Columns: {df.shape[1]}")
print(df.head())
print()

# ===============================================================
# 2. CLEAN DATA
# ===============================================================
# Remove duplicates
df = df.drop_duplicates(subset="ParticipantID")

# Drop rows with missing values in key fields
key_fields = ["Score", "TimeTaken-ms", "Motivation", "Engagement"]
df = df.dropna(subset=key_fields)

# Convert Yes/No columns to 1/0
for col in ["HasUpper", "HasLower", "HasDigit", "HasSpecial"]:
    if col in df.columns:
        df[col] = df[col].map({"Yes": 1, "No": 0})

# Convert time to seconds for readability
df["TimeTaken-s"] = df["TimeTaken-ms"] / 1000

print("=" * 60)
print("2. CLEANED DATA")
print("=" * 60)
print(f"Rows after cleaning: {df.shape[0]}")
print(df.isnull().sum())
print()

# ===============================================================
# 3. DESCRIPTIVE STATISTICS BY GROUP
# ===============================================================
print("=" * 60)
print("3. DESCRIPTIVE STATISTICS BY GROUP")
print("=" * 60)

metrics = ["Score", "TimeTaken-s", "PasswordLength", "Motivation", "Engagement"]
summary = df.groupby("Group")[metrics].agg(["mean", "std", "median"])
print(summary.round(2))
print()

# ===============================================================
# 4. INDEPENDENT SAMPLES T-TEST
# ===============================================================
print("=" * 60)
print("4. T-TEST RESULTS (Control vs Experiment)")
print("=" * 60)

control = df[df["Group"] == "control"]
experiment = df[df["Group"] == "experiment"]

for metric in metrics:
    t, p = stats.ttest_ind(control[metric], experiment[metric],
                           equal_var=False)
    sig = "Significant" if p < 0.05 else "Not significant"
    print(f"{metric:15s}  t = {t:7.3f}   p = {p:.4f}   {sig}")
print()

# Effect size (Cohen's d)
def cohens_d(a, b):
    n1, n2 = len(a), len(b)
    var1, var2 = np.var(a, ddof=1), np.var(b, ddof=1)
    pooled = np.sqrt(((n1 - 1) * var1 + (n2 - 1) * var2) / (n1 + n2 - 2))
    return (np.mean(a) - np.mean(b)) / pooled

print("=" * 60)
print("5. EFFECT SIZE (Cohen's d)")
print("=" * 60)
for metric in metrics:
    d = cohens_d(control[metric], experiment[metric])
    size = "small" if abs(d) < 0.5 else "medium" if abs(d) < 0.8 else "large"
    print(f"{metric:15s}  d = {d:6.3f}   ({size})")
print()

# ===============================================================
# 6. VISUALISATIONS
# ===============================================================
sns.set_style("whitegrid")
plt.rcParams["figure.facecolor"] = "white"

# --- Box plots ---
fig, axes = plt.subplots(2, 2, figsize=(14, 10))
fig.suptitle("Control vs Experiment Group Comparison",
             fontsize=16, fontweight="bold")

sns.boxplot(data=df, x="Group", y="Score",
            palette=["#E3F2FD", "#FFEBEE"], ax=axes[0, 0])
axes[0, 0].set_title("Password Score (0–4)")

sns.boxplot(data=df, x="Group", y="TimeTaken-s",
            palette=["#E3F2FD", "#FFEBEE"], ax=axes[0, 1])
axes[0, 1].set_title("Time Taken (seconds)")

sns.boxplot(data=df, x="Group", y="Motivation",
            palette=["#E3F2FD", "#FFEBEE"], ax=axes[1, 0])
axes[1, 0].set_title("Motivation (1–5)")

sns.boxplot(data=df, x="Group", y="Engagement",
            palette=["#E3F2FD", "#FFEBEE"], ax=axes[1, 1])
axes[1, 1].set_title("Engagement (1–5)")

plt.tight_layout()
plt.savefig("Analysis_Boxplots.png", dpi=300, facecolor="white")
plt.close()
print("Saved: Analysis_Boxplots.png")

# --- Bar chart of means ---
means = df.groupby("Group")[metrics].mean()
fig, ax = plt.subplots(figsize=(10, 5))
means.T.plot(kind="bar", ax=ax,
             color=["#0D47A1", "#B71C1C"],
             edgecolor="black")
ax.set_title("Mean Values per Metric (Control vs Experiment)",
             fontsize=13, fontweight="bold")
ax.set_ylabel("Mean Value")
ax.set_xticklabels(ax.get_xticklabels(), rotation=0)
plt.tight_layout()
plt.savefig("Analysis_BarChart.png", dpi=300, facecolor="white")
plt.close()
print("Saved: Analysis_BarChart.png")

# --- Correlation heatmap ---
corr = df[metrics].corr()
plt.figure(figsize=(8, 6))
sns.heatmap(corr, annot=True, cmap="Blues", fmt=".2f",
            linewidths=0.5, vmin=-1, vmax=1)
plt.title("Correlation Matrix", fontsize=13, fontweight="bold")
plt.tight_layout()
plt.savefig("Analysis_Correlation.png", dpi=300, facecolor="white")
plt.close()
print("Saved: Analysis_Correlation.png")

print("\nAnalysis complete.")