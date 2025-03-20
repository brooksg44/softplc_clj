# SoftPLC_Clj

A Clojure implementation of a Software PLC (Programmable Logic Controller) with ladder logic visualization using the Quil library.

## Overview

SoftPLC_Clj is a functional Programmable Logic Controller (PLC) simulation environment implemented in Clojure. It allows you to:

- Write PLC programs in Instruction List (IL) format
- Visualize programs in Ladder Diagram (LD) format
- Monitor PLC execution in real-time
- Interact with input/output variables

This project demonstrates Clojure's ability to handle industrial automation concepts in a functional programming paradigm.

## Features

- **PLC Runtime**: Executes ladder logic programs with configurable scan rates
- **Ladder Visualization**: Displays ladder diagrams using Quil (Processing for Clojure)
- **Data Table**: Manages boolean and word variables (X, Y, C, DS, DD, DF types)
- **IL Compiler**: Converts Instruction List programs to executable code
- **Monitoring**: Real-time variable monitoring and statistics tracking

## Installation

### Prerequisites

- JDK 11 or higher
- [Leiningen](https://leiningen.org/) 2.0.0 or higher

### Building from Source

```bash
git clone https://github.com/yourusername/softplc-clj.git
cd softplc-clj
lein deps
```

## Usage

### Running the Application

```bash
lein run
```

Or with custom settings:

```bash
lein run -p myprogram.txt -s 100
```

Options:
- `-p, --program PROGRAM`: PLC Program file to load (default: "simpleconveyor.txt")
- `-s, --scan-rate RATE`: PLC Scan rate in milliseconds (default: 500)
- `-c, --config CONFIG`: Configuration file (default: "config.edn")
- `-h, --help`: Show help information

### Creating a Standalone JAR

```bash
lein uberjar
java -jar target/softplc-clj-0.1.0-SNAPSHOT-standalone.jar
```

## PLC Programming

SoftPLC_Clj uses a simplified Instruction List (IL) format for programming. Here's a simple example:

```
# Simple start/stop circuit for a conveyor
NETWORK 1
STR X1    # E-Stop (normally closed)
AND X2    # Stop Button (normally closed)
STR X3    # Start Button
OR Y1     # Latch the output
ANDSTR    # AND with the E-Stop and Stop conditions
OUT Y1    # Output to Motor Contactor

NETWORK 2
END
```

### Supported Instructions

- `STR` - Store a bit to the stack
- `AND` - Logical AND with stack
- `OR` - Logical OR with stack
- `NOT` - Logical NOT
- `ANDSTR` - AND with a stored value
- `ORSTR` - OR with a stored value
- `OUT` - Output result to a variable
- `SET` - Set a variable if result is true
- `RST` - Reset a variable if result is true
- `END` - End of program

### Variable Types

- `X` - Input boolean variable
- `Y` - Output boolean variable
- `C` - Control/internal boolean variable
- `DS` - Single word variable (16-bit)
- `DD` - Double word variable (32-bit)
- `DF` - Floating point variable

## Development

### Running Tests

```bash
lein test
```

### REPL Development

```clojure
;; Start the application from REPL
(require '[softplc-clj.core :as core])
(core/start!)

;; Manipulate data table values
(require '[softplc-clj.data-table :as dt])
(dt/set-bool! "X1" true)
(dt/get-bool "Y1")
```

## License

This project is licensed under the GNU General Public License v3.0 - see the LICENSE file for details.

## Acknowledgements

- This project is inspired by various open-source PLC implementations
- Built using [Quil](http://quil.info/) for visualization
- Special thanks to the original Python SoftPLC project that served as inspiration
