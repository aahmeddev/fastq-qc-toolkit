import sys
import pandas as pd
import matplotlib
# Set the backend to 'Agg' to prevent Matplotlib from trying to open a GUI window.
# This is essential for running the script in a headless environment (e.g., from a Java backend).
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import argparse
from matplotlib.ticker import MultipleLocator, MaxNLocator

# An alternative way to set the backend, ensuring it's done before any plotting.
plt.switch_backend('Agg')

def create_simple_line(data_source, output_path, title, xlabel, ylabel, ymin=None, ymax=None, xmin=None, xmax=None, xtick=None, ytick=None):
    """
    Generates a simple line plot from CSV data read from a specified source.

    Args:
        data_source: A file-like object (e.g., sys.stdin) to read CSV data from.
        output_path (str): The path to save the generated PNG image.
        title (str): The title of the plot.
        xlabel (str): The label for the x-axis.
        ylabel (str): The label for the y-axis.
        ymin (float, optional): The minimum value for the y-axis. Defaults to None.
        ymax (float, optional): The maximum value for the y-axis. Defaults to None.
        xmin (int, optional): The minimum value for the x-axis. Defaults to None.
        xmax (int, optional): The maximum value for the x-axis. Defaults to None.
        xtick (int, optional): The spacing for major ticks on the x-axis. Defaults to None.
        ytick (int, optional): The spacing for major ticks on the y-axis. Defaults to None.
    """
    fig = None # Initialize fig to None to handle it in the finally block.
    try:
        # Read the CSV data from the provided source (e.g., stdin) into a pandas DataFrame.
        df = pd.read_csv(data_source)
        if df.empty:
            print("Warning: No data received, plot will not be generated.", file=sys.stderr)
            return

        # Create a Matplotlib figure and axis object.
        fig, ax = plt.subplots(figsize=(10, 6))

        # --- Plotting Logic ---
        # If there's only one data point, draw a scatter plot with a single point instead of a line.
        if len(df) == 1:
            x_val = df.iloc[0, 0]
            y_val = df.iloc[0, 1]
            ax.scatter(x_val, y_val, s=100, color='#6B2A4E', zorder=5) # Draw a prominent point.
            # Annotate the point with its coordinates for clarity.
            ax.annotate(f'({x_val}, {y_val:.2f})', (x_val, y_val), textcoords="offset points", xytext=(0,10), ha='center')
        else:
            # If there are multiple points, draw a standard line plot.
            ax.plot(df.iloc[:, 0], df.iloc[:, 1], color='#6B2A4E')

        # --- Axis Ticks ---
        # Set the spacing of x-axis ticks if a value is provided.
        if xtick is not None:
            ax.xaxis.set_major_locator(MultipleLocator(xtick))
        else:
            # As a fallback for line plots, ensure x-axis ticks are integers if possible.
            if len(df) > 1:
                ax.xaxis.set_major_locator(MaxNLocator(integer=True))

        # Set the spacing of y-axis ticks if a value is provided.
        if ytick is not None:
            ax.yaxis.set_major_locator(MultipleLocator(ytick))

        # --- Plot Styling and Labels ---
        ax.set_title(title, fontsize=16)
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
        ax.grid(True, linestyle='--', alpha=0.6) # Add a faint grid for readability.

        # Set y-axis limits if provided.
        if ymin is not None and ymax is not None:
            ax.set_ylim(bottom=ymin, top=ymax)

        # Set x-axis limits if provided.
        if xmin is not None and xmax is not None:
            ax.set_xlim(left=xmin, right=xmax)

        # --- Save Output ---
        plt.tight_layout() # Adjust plot to ensure everything fits without overlapping.
        fig.savefig(output_path, dpi=120) # Save the figure to the specified file path.

    except Exception as e:
        # Catch any errors during the process and print them to stderr.
        print(f"An error occurred in Python script: {e}", file=sys.stderr)
    finally:
        # Ensure the figure is closed to free up memory, even if an error occurred.
        if fig is not None:
            plt.close(fig)

if __name__ == '__main__':
    # This block sets up the command-line argument parser.
    # It allows the script to be called with various options from another process (like the Java app).
    parser = argparse.ArgumentParser(description="Generate a simple line plot from CSV data.")
    parser.add_argument('output_file', help="The path to save the output PNG image.")
    parser.add_argument('--title', help="Plot title")
    parser.add_argument('--xlabel', help="X-axis label")
    parser.add_argument('--ylabel', help="Y-axis label")
    parser.add_argument('--ymin', type=float, help="Y-axis minimum")
    parser.add_argument('--ymax', type=float, help="Y-axis maximum")
    parser.add_argument('--xmin', type=int, help="X-axis minimum")
    parser.add_argument('--xmax', type=int, help="X-axis maximum")
    parser.add_argument('--xtick', type=int, help="Spacing for X-axis ticks.")
    parser.add_argument('--ytick', type=int, help="Spacing for Y-axis ticks.")

    # Parse the arguments provided at the command line.
    args = parser.parse_args()

    # Call the main plotting function with the parsed arguments.
    # Data is read from standard input (piped from the Java application).
    create_simple_line(
        data_source=sys.stdin,
        output_path=args.output_file,
        title=args.title,
        xlabel=args.xlabel,
        ylabel=args.ylabel,
        ymin=args.ymin,
        ymax=args.ymax,
        xmin=args.xmin,
        xmax=args.xmax,
        xtick=args.xtick,
        ytick=args.ytick
    )
