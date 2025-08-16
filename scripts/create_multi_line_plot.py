import matplotlib.pyplot as plt
import pandas as pd
import sys

def create_multi_line_plot(output_image_path):
    """
    Reads per-base content percentages from stdin and generates a multi-line plot.
    This is typically used to visualize the proportion of A, C, G, and T bases
    at each position along a set of DNA sequences.

    The script expects CSV data from stdin with the following columns:
    'position', 'A_percent', 'C_percent', 'G_percent', 'T_percent'.

    Args:
        output_image_path (str): The file path where the generated plot image will be saved.
    """
    try:
        # Read the CSV data piped from the Java application into a pandas DataFrame.
        df = pd.read_csv(sys.stdin)
        if df.empty:
            print("Warning: No data received from stdin. Plot will not be generated.", file=sys.stderr)
            return

        # Create a Matplotlib figure and axis object to draw on.
        fig, ax = plt.subplots(figsize=(10, 6))

        # --- Plotting ---
        # Draw four separate lines on the plot, one for each DNA base.
        # The colors used are standard for bioinformatics sequence content plots.
        ax.plot(df['position'], df['A_percent'], color='green', label='% A')
        ax.plot(df['position'], df['C_percent'], color='blue', label='% C')
        ax.plot(df['position'], df['G_percent'], color='black', label='% G') # Black or yellow is common for Guanine.
        ax.plot(df['position'], df['T_percent'], color='red', label='% T')


        # --- Styling and Labels ---
        ax.set_title('Sequence Content Across All Bases', fontsize=16)
        ax.set_xlabel('Position in Read (bp)')
        ax.set_ylabel('Percent (%)')

        # Add a legend to identify which line corresponds to which base.
        # Place it outside the top-right corner of the plot area.
        ax.legend(bbox_to_anchor=(1.01, 1), loc='upper left')

        # Add a horizontal grid for easier reading of percentage values.
        ax.grid(True, which='major', axis='y', linestyle='--', linewidth=0.7)
        # Set the x-axis to span the full range of positions in the data.
        ax.set_xlim(df['position'].min(), df['position'].max())
        # Set the y-axis from 0 to 100 percent.
        ax.set_ylim(0, 100)

        # Create alternating vertical background bands to improve readability of positions.
        # This visually groups the data for every other tick mark.
        for i, tick in enumerate(ax.get_xticks()):
            if i % 2 == 1 and i + 1 < len(ax.get_xticks()):
                # axvspan creates a vertical rectangle spanning the y-axis.
                ax.axvspan(tick, ax.get_xticks()[i+1], facecolor='black', alpha=0.07)


        # --- Save Output ---
        # Save the figure to the path specified by the command-line argument.
        # bbox_inches='tight' ensures the legend and labels are not cut off.
        plt.savefig(output_image_path, dpi=120, bbox_inches='tight')
        print(f"Successfully saved multi-line plot to {output_image_path}")

    except Exception as e:
        # Catch any unexpected errors and print them to stderr for the calling process to see.
        print(f"An error occurred in Python script: {e}", file=sys.stderr)
        # It's good practice to exit with a non-zero status code on error.
        sys.exit(1)

if __name__ == '__main__':
    # This block is executed when the script is run directly.
    # It checks that exactly one command-line argument (the output path) is provided.
    if len(sys.argv) != 2:
        print("Usage: python create_multi_line_plot.py <output_image_path>", file=sys.stderr)
        sys.exit(1)

    # Call the main function with the output path from the command-line arguments.
    create_multi_line_plot(sys.argv[1])
