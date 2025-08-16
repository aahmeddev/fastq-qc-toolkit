import matplotlib.pyplot as plt
import pandas as pd
import sys
import argparse

def create_kmer_plot(data_source, output_path, title, xlabel):
    """
    Reads K-mer count or percentage data from a specified source and generates
    a styled horizontal bar chart.

    The script expects CSV data from stdin with two columns: 'kmer' and a value column.
    It uses command-line arguments to customize the plot's title and x-axis label.

    Args:
        data_source: A file-like object (e.g., sys.stdin) to read CSV data from.
        output_path (str): The file path where the generated plot image will be saved.
        title (str): The title for the plot.
        xlabel (str): The label for the x-axis.
    """
    try:
        # Read the CSV data piped from the Java application into a pandas DataFrame.
        df = pd.read_csv(data_source)
        if df.empty:
            print("Warning: No data received, K-mer plot will not be generated.", file=sys.stderr)
            return

        # Reverse the DataFrame so that when plotted with barh, the K-mer
        # with the highest value appears at the top of the chart.
        df = df.iloc[::-1]

        # Create a Matplotlib figure and axis object.
        fig, ax = plt.subplots(figsize=(10, 8))

        # --- Plotting ---
        # The K-mer sequences are in the first column ('kmer'), and the values to plot are in the second.
        values = df.iloc[:, 1]
        # Create a horizontal bar chart (barh).
        ax.barh(df['kmer'], values, color='#1f77b4', edgecolor='black')

        # --- Styling and Labels ---
        ax.set_title(title, fontsize=16)
        ax.set_xlabel(xlabel)  # Use the xlabel passed as an argument.
        ax.set_ylabel('K-mer Sequence')
        ax.grid(True, axis='x', linestyle='--', alpha=0.6) # Add a vertical grid for readability.
        ax.margins(y=0.01) # Add a small margin to the top and bottom of the y-axis.

        # Add value labels to the end of each bar for precise reading.
        for index, value in enumerate(values):
            # Check if the x-axis label contains '%' to format the label accordingly.
            if '%' in xlabel:
                ax.text(value, index, f' {value:.2f}%', va='center')
            else:
                ax.text(value, index, f' {value}', va='center')

        # --- Save Output ---
        plt.tight_layout() # Adjust layout to prevent labels from being cut off.
        plt.savefig(output_path, dpi=120)
        print(f"Successfully saved K-mer plot to {output_path}")

    except Exception as e:
        # Catch any unexpected errors and report them to stderr.
        print(f"An error occurred in Python script: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    # Set up the command-line argument parser.
    parser = argparse.ArgumentParser(description='Generate a horizontal bar chart for K-mer content.')
    parser.add_argument('output_path', help='The file path to save the output PNG.')
    parser.add_argument('--title', default='K-mer Content', help='The title of the chart.')
    # Add the xlabel as a customizable command-line argument.
    parser.add_argument('--xlabel', default='Count', help='The label for the X-axis.')
    args = parser.parse_args()

    # Call the main plotting function with data from stdin and parsed arguments.
    create_kmer_plot(sys.stdin, args.output_path, args.title, args.xlabel)
