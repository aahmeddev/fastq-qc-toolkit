import sys
import pandas as pd
import matplotlib
# Set the backend to 'Agg' to prevent Matplotlib from trying to open a GUI window.
# This is crucial for running the script in a headless environment.
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import argparse
from matplotlib.ticker import MultipleLocator, MaxNLocator
import numpy as np
from scipy.stats import norm

# Ensure the 'Agg' backend is used before any plotting commands.
plt.switch_backend('Agg')

def create_gc_distribution_plot(output_path, observed_file, theoretical_file,
                                title, xlabel, ylabel, observed_label, theoretical_label,
                                observed_color, theoretical_color, theoretical_linestyle,
                                xmin, xmax, xtick):
    """
    Generates a plot comparing observed GC content distribution with a theoretical
    normal distribution curve.

    Args:
        output_path (str): The path to save the generated PNG image.
        observed_file (str): Path to the CSV file with observed GC data.
        theoretical_file (str): Path to the CSV file with theoretical GC data.
        title (str): The title for the plot.
        xlabel (str): The label for the x-axis.
        ylabel (str): The label for the y-axis.
        observed_label (str): The legend label for the observed data line.
        theoretical_label (str): The legend label for the theoretical data line.
        observed_color (str): The color for the observed data line.
        theoretical_color (str): The color for the theoretical data line.
        theoretical_linestyle (str): The line style for the theoretical curve (e.g., '--').
        xmin (int): The minimum value for the x-axis.
        xmax (int): The maximum value for the x-axis.
        xtick (int): The spacing for major ticks on the x-axis.
    """
    fig = None # Initialize fig to None for the finally block.
    try:
        # --- Data Loading ---
        # Read observed data from the specified CSV file.
        df_observed = pd.read_csv(observed_file)

        # Read theoretical data from the specified CSV file.
        df_theoretical = pd.read_csv(theoretical_file)

        # Create a Matplotlib figure and axis object.
        fig, ax = plt.subplots(figsize=(10, 6))

        # --- Plotting ---
        # Plot the observed data distribution if the DataFrame is not empty.
        if not df_observed.empty:
            ax.plot(df_observed['gc_content'], df_observed['count'],
                    color=observed_color,
                    label=observed_label,
                    linewidth=2) # Use a thicker line for the main data.
        else:
            print("Warning: No observed GC content data to plot.", file=sys.stderr)

        # Plot the theoretical distribution overlay if the DataFrame is not empty.
        if not df_theoretical.empty:
            ax.plot(df_theoretical['gc_content'], df_theoretical['count'],
                    color=theoretical_color,
                    label=theoretical_label,
                    linestyle=theoretical_linestyle,
                    linewidth=2)
        else:
            print("Warning: No theoretical GC content data to plot.", file=sys.stderr)


        # --- Styling and Labels ---
        ax.set_title(title, fontsize=16)
        ax.set_xlabel(xlabel)
        ax.set_ylabel(ylabel)
        ax.grid(True, linestyle='--', alpha=0.6) # Add a faint grid for readability.

        # Set X-axis tick spacing.
        if xtick is not None:
            ax.xaxis.set_major_locator(MultipleLocator(xtick))
        else:
            # Fallback to automatically determined integer ticks if no spacing is given.
            ax.xaxis.set_major_locator(MaxNLocator(integer=True))

        # Set X-axis limits.
        if xmin is not None and xmax is not None:
            ax.set_xlim(left=xmin, right=xmax)
        else:
            ax.set_xlim(left=0, right=100) # Default for GC content percentage.

        # Ensure the Y-axis starts at 0 for accurate representation of counts.
        ax.set_ylim(bottom=0)

        # Add the legend to the plot to identify the data series.
        ax.legend()

        # --- Save Output ---
        plt.tight_layout() # Adjust plot to fit all elements without overlap.
        fig.savefig(output_path, dpi=120)

    except Exception as e:
        # Catch any errors and report them to stderr for the calling process.
        print(f"An error occurred in Python script: {e}", file=sys.stderr)
        sys.exit(1)
    finally:
        # Ensure the figure is closed to free up memory.
        if fig is not None:
            plt.close(fig)

if __name__ == '__main__':
    # This block sets up the command-line argument parser.
    parser = argparse.ArgumentParser(description="Generate a GC content distribution plot with theoretical overlay.")
    parser.add_argument('output_file', help="The path to save the output PNG image.")
    parser.add_argument('--observed_file', help="Path to the CSV file containing observed GC content data.")
    parser.add_argument('--theoretical_file', help="Path to the CSV file containing theoretical GC content data.")
    parser.add_argument('--title', help="Plot title")
    parser.add_argument('--xlabel', help="X-axis label")
    parser.add_argument('--ylabel', help="Y-axis label")
    parser.add_argument('--observed_label', default="GC count per read", help="Label for the observed data series.")
    parser.add_argument('--theoretical_label', default="Theoretical Distribution", help="Label for the theoretical data series.")
    parser.add_argument('--observed_color', default="#800020", help="Color for the observed data line (e.g., #RRGGBB).")
    parser.add_argument('--theoretical_color', default="blue", help="Color for the theoretical data line.")
    parser.add_argument('--theoretical_linestyle', default="--", help="Line style for the theoretical data (e.g., '--', '-.', ':').")
    parser.add_argument('--xmin', type=int, default=0, help="X-axis minimum.")
    parser.add_argument('--xmax', type=int, default=100, help="X-axis maximum.")
    parser.add_argument('--xtick', type=int, default=2, help="Spacing for X-axis ticks.")

    # Parse the arguments provided from the command line.
    args = parser.parse_args()

    # Call the main plotting function with the parsed arguments.
    create_gc_distribution_plot(
        output_path=args.output_file,
        observed_file=args.observed_file,
        theoretical_file=args.theoretical_file,
        title=args.title,
        xlabel=args.xlabel,
        ylabel=args.ylabel,
        observed_label=args.observed_label,
        theoretical_label=args.theoretical_label,
        observed_color=args.observed_color,
        theoretical_color=args.theoretical_color,
        theoretical_linestyle=args.theoretical_linestyle,
        xmin=args.xmin,
        xmax=args.xmax,
        xtick=args.xtick
    )
