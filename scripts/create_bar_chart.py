import pandas as pd
import matplotlib.pyplot as plt
import sys
import argparse
import matplotlib

# Set the backend to 'Agg' to prevent Matplotlib from trying to open a GUI window.
# This is essential for running the script in a headless environment.
matplotlib.use('Agg')

def create_bar_chart(output_image_path, title, xlabel, ylabel, horizontal):
    """
    Reads CSV data from stdin and generates either a vertical or horizontal bar chart.

    The script is controlled by command-line arguments that define the chart's
    title, axis labels, and orientation.

    Args:
        output_image_path (str): The file path where the generated plot image will be saved.
        title (str): The title for the chart.
        xlabel (str): The label for the x-axis.
        ylabel (str): The label for the y-axis.
        horizontal (bool): If True, a horizontal bar chart is created. Otherwise, a vertical one is made.
    """
    try:
        # Read the CSV data piped from the Java application into a pandas DataFrame.
        df = pd.read_csv(sys.stdin)

        if df.empty:
            print("Warning: No data received. Bar chart will not be generated.", file=sys.stderr)
            return

        # Use a pre-defined Matplotlib style for a clean look with a grid.
        plt.style.use('seaborn-v0_8-whitegrid')
        fig, ax = plt.subplots(figsize=(10, 6))

        # Dynamically get the column names from the DataFrame.
        # Assumes the first column is for categories and the second is for values.
        x_col = df.columns[0]
        y_col = df.columns[1]

        # --- Plotting Logic ---
        # Check the 'horizontal' flag to determine the chart's orientation.
        if horizontal:
            # Create a horizontal bar chart using ax.barh().
            ax.barh(df[x_col], df[y_col], color='#007acc')
            ax.set_xlabel(xlabel)
            ax.set_ylabel(ylabel)
        else:
            # Create a standard vertical bar chart using ax.bar().
            ax.bar(df[x_col], df[y_col], color='#007acc')
            ax.set_xlabel(xlabel)
            ax.set_ylabel(ylabel)
        # --- End Plotting Logic ---

        # --- Styling and Labels ---
        ax.set_title(title, fontsize=16)
        # Rotate x-axis labels for better readability if they are long.
        plt.xticks(rotation=45, ha='right')
        # Adjust layout to prevent labels from being cut off.
        plt.tight_layout()

        # --- Save Output ---
        plt.savefig(output_image_path, dpi=120)
        print(f"Successfully saved bar chart to {output_image_path}")

    except Exception as e:
        # Catch any unexpected errors and report them to stderr for the calling process.
        print(f"An error occurred in create_bar_chart.py: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    # This block sets up the command-line argument parser.
    parser = argparse.ArgumentParser(description='Generate a bar chart from CSV data.')
    parser.add_argument('output_image_path', help='The path to save the output image.')
    parser.add_argument('--title', default='Bar Chart', help='The title of the chart.')
    parser.add_argument('--xlabel', default='X-axis', help='The label for the x-axis.')
    parser.add_argument('--ylabel', default='Y-axis', help='The label for the y-axis.')

    # Add a flag for creating a horizontal chart. `action='store_true'` means the
    # argument will be True if the flag is present, and False otherwise.
    parser.add_argument('--horizontal', action='store_true', help='If set, create a horizontal bar chart.')

    # Parse the arguments provided from the command line.
    args = parser.parse_args()

    # Call the main plotting function with the parsed arguments.
    create_bar_chart(args.output_image_path, args.title, args.xlabel, args.ylabel, args.horizontal)
