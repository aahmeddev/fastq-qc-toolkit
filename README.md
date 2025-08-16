# DNA Quality Control Toolkit

A JavaFX application for analyzing FASTQ files to assess sequence quality. It provides a suite of tools for quality control, including various plots and statistical reports.

![Screenshot of the application's main window](/welcome_page.png)

## Key Features

* **Interactive QC Reports:** Analyze `.fastq` and `.fastq.gz` files and view results in an interactive tabbed interface.
* **Comprehensive Plots:** Generates a full suite of quality plots, including:
    * Per Base Sequence Quality
    * Per Sequence Quality Scores
    * Sequence Duplication Levels
    * Adapter Content
* **Parallel Processing:** Leverages multi-core CPUs to analyze large FASTQ files significantly faster.
* **Export Reports:** Save complete analysis results as a self-contained HTML file.
* **Adapter & Quality Trimming:** Trim low-quality bases and adapter sequences from your files.
* **Quality of Life:** Convenient features including drag-and-drop file support, a multi-tabbed interface for handling simultaneous analyses, and processing limits for quick data previews.

## Acknowledgments

* This project was heavily inspired by the original [FastQC](https://www.bioinformatics.babraham.ac.uk/projects/fastqc/) tool.

## Setup & Installation

### Prerequisites

* Java Development Kit (JDK) 17 or newer
* Apache Maven
* Python 3.8 or newer
* A JavaFX SDK compatible with your JDK version

### Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/aahmeddev/QC-Toolkit.git
    cd your-repository-name
    ```

2.  **Set up the Python environment:**
    ```bash
    python -m venv python_env
    # On Windows
    python_env\Scripts\activate
    # On macOS/Linux
    source python_env/bin/activate
    ```

3.  **Install Python dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

## How to Run

You can build and run the application using Maven. Make sure to provide the path to your JavaFX SDK.

```bash
# Example for Windows
mvn clean javafx:run -Djavafx.sdk.path="C:\path\to\javafx-sdk-21"

# Example for macOS/Linux
mvn clean javafx:run -Djavafx.sdk.path="/path/to/javafx-sdk-21"