public class CompilationEngine {
    private static String whileCON = "WHILE_CON";
    private static String whileEND = "WHILE_END";
    private static String ifTrue = "IF_TRUE";
    private static String ifFalse = "IF_FALSE";

    private JackTokenizer tokenizer;
    private SymbolTable table;
    private VMWriter writer;
    private String className;
    private String currentName;
    private int currentKind;
    private String currentType;
    private int whileCounter, ifCounter;

    public CompilationEngine(String filename) {
        tokenizer = new JackTokenizer(filename);
        table = new SymbolTable();
        String outputName = filename.split("\\.")[0] + ".vm";
        writer = new VMWriter(outputName);
	}

    private void addToTable() { 
        table.define(currentName, currentType, currentKind); 
    }

    public void compileClass() {
        tokenizer.advance();
        if (tokenizer.keyword() == JackTokens.CLASS) {
            tokenizer.advance();
            if (checkIdentifier()) {
                className = tokenizer.identifier();
            } else {
                System.out.println("illegal class name identifier");
                return;
            }

            tokenizer.advance();
            if (!checkSymbol("{")) {
                System.out.println("no openning { for class");
                return;
            }

            tokenizer.advance();
            while ( tokenizer.keyword() == JackTokens.STATIC ||
                    tokenizer.keyword() == JackTokens.FIELD) {
                compileClassVarDec();
                tokenizer.advance();
            }

            while ( tokenizer.keyword() == JackTokens.CONSTRUCTOR ||
                    tokenizer.keyword() == JackTokens.FUNCTION ||
                    tokenizer.keyword() == JackTokens.METHOD) {
                compileSubRoutine();
                tokenizer.advance();
            }

            if (!checkSymbol("}")) {
                System.out.println("no closing } for class");
                return;
            }

            if (tokenizer.hasMoreTokens()) {
                System.out.println("addtional tokens after closing }");
            }
        } else {
            System.out.println("does not start with class");
            return;
        }
    }
    
    public void compileClassVarDec() {
        currentKind = tokenizer.keyword();

        tokenizer.advance();

        if (!checkAndWriteType()) {
            System.out.println("illegal type for class var dec");
            return;
        }

        tokenizer.advance();
        
        if (checkIdentifier()) {
            addToTable();
        } else {
            System.out.println("illegal classVar identifier");
            return;
        }
        

        tokenizer.advance();
        while (tokenizer.symbol().equals(",")) {
            tokenizer.advance();
            if (checkIdentifier()) {
                addToTable();
            } else {
                System.out.println("illegal classVar identifier");
                return;
            }
            tokenizer.advance();
        }

        if (!checkSymbol(";")) {
            System.out.println("no ending ;");
            return;
        }
    }

    public void compileSubRoutine() {
        table.startSubroutine();

        int subRoutineKind = tokenizer.keyword();

        tokenizer.advance();
        if (!checkAndWriteType()) {
            System.out.println("Illegal type name for subroutine");
            return;
        }

        String currentSubName = null;
        tokenizer.advance();
        if (checkIdentifier()) {
            currentSubName = className + "." + currentName;
        } else {
            System.out.println("illegal subroutine name");
            return;
        }
        
        if (subRoutineKind == JackTokens.METHOD) {
            table.define("this", className, JackTokens.ARG);
        }

        tokenizer.advance();
        if (checkSymbol("(")) {
            compileParameterList();
        } else {
            System.out.println("no () after function name");
            return;
        }

        if (!checkSymbol(")")) {
            System.out.println("no () after function name");
            return;
        }

        tokenizer.advance();
        if (checkSymbol("{")) {
            tokenizer.advance();
            while ( tokenizer.tokenType() == JackTokens.KEYWORD &&
                    tokenizer.token().equals("var")) {
                compileVarDec();
                tokenizer.advance();
            }
        } else {
            System.out.println("no { after function parameters");
            return;
        }
         
        writer.writeFunction(currentSubName, table.varCount(JackTokens.VAR));

        if (subRoutineKind == JackTokens.CONSTRUCTOR) {
            int numOfFields = table.varCount(JackTokens.FIELD);
            if (numOfFields > 0) writer.writePush("constant", numOfFields);
            writer.writeCall("Memory.alloc", 1);
            writer.writePop("pointer", 0);
        } else if (subRoutineKind == JackTokens.METHOD) {
            writer.writePush("argument", 0);
            writer.writePop("pointer", 0);
        }

        compileStatements();

        if (!checkSymbol("}")) {
            System.out.println("no } found to close subroutine call");
            System.out.printf("current token is : %s\n", tokenizer.token());
        }

        return;
    }

    public int compileParameterList() {
        currentKind = JackTokens.ARG;
        int numberOfArgs = 0;

        tokenizer.advance();
        if (checkAndWriteType()) {
            tokenizer.advance();
            if (checkIdentifier()) {
                addToTable();
                numberOfArgs++;
            } else {
                System.out.println("illegal identifier in parameter list");
                return -1;
            }

            tokenizer.advance();
            while (tokenizer.symbol().equals(",")) {
                tokenizer.advance();
                if (!checkAndWriteType()) {
                    System.out.println("illegal type name");
                    return -1;
                }
                tokenizer.advance();
                if (checkIdentifier()) {
                    addToTable();
                    numberOfArgs++;
                } else {
                    System.out.println("illegal identifier name");
                    return -1;
                }
                tokenizer.advance();
            }
        }
        return numberOfArgs;
    }

    public void compileVarDec() {
        currentKind = JackTokens.VAR;

        tokenizer.advance();
        if (!checkAndWriteType()) {
            System.out.println("illegal type for var");
            return;
        }

        tokenizer.advance();
        if (checkIdentifier()) {
            System.out.printf("adding: %s\n", currentName);
            addToTable();
        } else {
            System.out.println("illegal identifier for var");
            return;
        }

        tokenizer.advance();
        while (tokenizer.symbol().equals(",")) {
            tokenizer.advance();
            if (checkIdentifier()) {
                System.out.printf("adding: %s\n", currentName);
                addToTable();
            } else {
                System.out.println("illegal identifier for var");
                return;
            }

            tokenizer.advance();
        }

        if (!checkSymbol(";")) {
            System.out.println("varDec doesn't end with ;");
            return;
        }
    }

    public void compileStatements() {
        while (tokenizer.tokenType() == JackTokens.KEYWORD) {
            int keyword_type = tokenizer.keyword();
            switch(keyword_type) {
                case JackTokens.LET:    compileLet(); tokenizer.advance(); break;
                case JackTokens.IF:     compileIf(); break;
                case JackTokens.WHILE:  compileWhile(); tokenizer.advance(); break;
                case JackTokens.DO:     compileDo(); tokenizer.advance(); break;
                case JackTokens.RETURN: compileReturn(); tokenizer.advance(); break;
                default: System.out.println("illegal statement"); return;
            }
        }
    }

    private boolean checkAndWriteType() {
        if (tokenizer.keyword() == JackTokens.INT ||
            tokenizer.keyword() == JackTokens.CHAR ||
            tokenizer.keyword() == JackTokens.BOOLEAN) {
            currentType = tokenizer.token();
            return true;
        } else if (tokenizer.tokenType() == JackTokens.IDENTIFIER) {
            currentType = tokenizer.token();
            return true;
        } else if (tokenizer.tokenType() == JackTokens.KEYWORD && tokenizer.token().equals("void")) {
            currentType = tokenizer.token();
            return true;
        } else {
            return false;
        }
    }

    private boolean checkIdentifier() {
        if (tokenizer.tokenType() == JackTokens.IDENTIFIER) {
            currentName = tokenizer.identifier();
            return true;
        } else {
            return false;
        }
    }

    private boolean checkSymbol(String s) {
        if (tokenizer.symbol().equals(s)) return true; 
        else                              return false;
    }

    private boolean checkKeyword(String k) {
        if (tokenizer.tokenType() == JackTokens.KEYWORD &&
            tokenizer.token().equals(k)) {
            return true;
        } else {
            return false;
        }
    }

    public void compileLet() {
        tokenizer.advance();
        if (!checkIdentifier()) {
            System.out.println("Illegal identifier");
            return;
        }

        String var = currentName;
        boolean isArray = false;
        String kind = table.kindOf(var);
        String type = table.typeOf(var);
        int index = table.indexOf(var);

        tokenizer.advance();
        if (checkSymbol("[")) {
            compileArrayTerm();
            isArray = true;

            writer.writePush(kind, index);
            writer.writeArithmetic("add");
            writer.writePop("temp", 2);

            tokenizer.advance();
        }

        if (!checkSymbol("=")) {
            System.out.println("No = found");
            return;
        }

        tokenizer.advance();
        compileExpression();
        
        if (isArray) {
            writer.writePush("temp", 2);
            writer.writePop("pointer", 1);
            writer.writePop("that", 0);
        } else {
            writer.writePop(kind, index);
        }

        if (!checkSymbol(";")) {
            System.out.println("No ; found at the end of statement");
            return;
        }
    }

    public void compileIf() {
        int localCounter = ifCounter++;

        tokenizer.advance();
        if (!checkSymbol("(")) {
            System.out.println("No openning ( for if statement");
            return;
        }

        tokenizer.advance();
        compileExpression();

        if (!checkSymbol(")")) {
            System.out.println("No closing ) for if statement");
            return;
        }

        writer.writeArithmetic("not");

        tokenizer.advance();
        if (!checkSymbol("{")) {
            System.out.println("No { for if statement");
            return;
        }

        writer.writeIf(ifFalse, localCounter);
        tokenizer.advance();
        compileStatements();

        writer.writeGoto(ifTrue, localCounter);

        if (!checkSymbol("}")) {
            System.out.println("No } for if statement");
            System.out.printf("the current symbol is %s\n", tokenizer.token());
            return;
        }
        writer.writeLabel(ifFalse, localCounter);

        tokenizer.advance();
        if (checkKeyword("else")) {
            tokenizer.advance();
            if (!checkSymbol("{")) {
                System.out.println("No { for else statment");
                return;
            }

            tokenizer.advance();
            compileStatements();

            if (!checkSymbol("}")) {
                System.out.println("No } for if statement");
                return;
            }
            tokenizer.advance();
        }

        writer.writeLabel(ifTrue, localCounter);
    }

    public void compileWhile() {
        int localCounter = whileCounter++;

        writer.writeLabel(whileCON, localCounter);
        
        tokenizer.advance();
        if (!checkSymbol("(")) {
            System.out.println("No ( in while statement");
            return;
        }

        tokenizer.advance();
        compileExpression();

        if (!checkSymbol(")")) {
            System.out.println("No ) in while statement");
            return;
        }
        
        writer.writeArithmetic("not");

        tokenizer.advance();
        if (!checkSymbol("{")) {
            System.out.println("No { in while statement");
            return;
        }
        writer.writeIf(whileEND, localCounter);

        tokenizer.advance();
        compileStatements();

        writer.writeGoto(whileCON, localCounter);
        
        if (!checkSymbol("}")) {
            System.out.println("No } in while statement");
            return;
        }

        writer.writeLabel(whileEND, localCounter);
    }

    public void compileDo() {
        tokenizer.advance();
        if (checkIdentifier()) {
            String firstHalf = currentName;
            tokenizer.advance();
            if (checkSymbol(".") || checkSymbol("(")) {
                compileSubRoutineCall(firstHalf);
            } else {
                System.out.println("Not valid subroutine call");
                return;
            }
            
            
        } else {
            System.out.printf("%s is not a valid identifier for do statement\n", tokenizer.token());
            return;
        }

        tokenizer.advance();
        if (!checkSymbol(";")) {
            System.out.println("No closing ;");
            return;
        }
        writer.writePop("temp", 0);
    }

    public void compileReturn() {
        tokenizer.advance();
        if (!checkSymbol(";")) {
            compileExpression();

            if (!checkSymbol(";")) {
                System.out.println("return statement not ending with ;");
                return;
            }
        } else {
            writer.writePush("constant", 0);
        }
        writer.writeReturn();
    }

    public void compileExpression() {
        compileTerm();

        while (checkSymbol("+") || checkSymbol("-") || checkSymbol("*") || checkSymbol("/") ||
               checkSymbol("&") || checkSymbol("|") || checkSymbol("<") || checkSymbol(">") ||
               checkSymbol("=")) {
            String localSymbol = tokenizer.symbol(); 
            tokenizer.advance();
            compileTerm();
            if (localSymbol.equals("+")) {
                writer.writeArithmetic("add");
            } else if (localSymbol.equals("-")) {
                writer.writeArithmetic("sub");
            } else if (localSymbol.equals("*")) {
                writer.writeArithmetic("call Math.multiply 2");
            } else if (localSymbol.equals("/")) {
                writer.writeArithmetic("call Math.divide 2");
            } else if (localSymbol.equals("&")) {
                writer.writeArithmetic("and");
            } else if (localSymbol.equals("|")) {
                writer.writeArithmetic("or");
            } else if (localSymbol.equals("<")) {
                writer.writeArithmetic("lt");
            } else if (localSymbol.equals(">")) {
                writer.writeArithmetic("gt");
            } else if (localSymbol.equals("=")) {
                writer.writeArithmetic("eq");
            }
        }
    }

    public void compileTerm() {
        if (tokenizer.tokenType() == JackTokens.INT_CONST) {
                        writer.writePush("constant", tokenizer.intVal());
            tokenizer.advance();
        } else if (tokenizer.tokenType() == JackTokens. STRING_CONST) {
            String strLiteral = tokenizer.stringVal();
            writer.writePush("constant", strLiteral.length());
            System.out.println("here: " +strLiteral + " " + Integer.toString(strLiteral.length()));
            writer.writeCall("String.new", 1);
            for (int i = 0; i < strLiteral.length(); i++) {

                writer.writePush("constant", (int) strLiteral.charAt(i));
                writer.writeCall("String.appendChar", 2);
            }

            tokenizer.advance();
        } else if (checkKeyword("true") || checkKeyword("false") || checkKeyword("null") ||
                   checkKeyword("this")) {
            if (checkKeyword("null") || checkKeyword("false")) {
                writer.writePush("constant", 0);
            } else if (checkKeyword("true")) {
                writer.writePush("constant", 1);
                writer.writeArithmetic("neg");
            } else if (checkKeyword("this")) {
                writer.writePush("pointer", 0);
            }
            tokenizer.advance();
        } else if (checkSymbol("-") || checkSymbol("~")) {
            tokenizer.advance();
            String localSymbol = tokenizer.symbol(); 
            compileTerm();

            if (localSymbol.equals("-")) { 
                writer.writeArithmetic("neg");
            } else {
                writer.writeArithmetic("not");
            }
        } else if (checkIdentifier()) {
            String firstHalf = currentName;
            tokenizer.advance();
            if (checkSymbol("[")) {
                writer.writePush(table.kindOf(firstHalf), table.indexOf(firstHalf));
                compileArrayTerm();
                writer.writeArithmetic("add");
                writer.writePop("pointer", 1);
                writer.writePush("that", 0);
                tokenizer.advance();
            } else if (checkSymbol("(") || checkSymbol(".")) {
                compileSubRoutineCall(firstHalf);
                tokenizer.advance(); 
            } else {
                writer.writePush(table.kindOf(firstHalf), table.indexOf(firstHalf));
            }
        } else if (tokenizer.tokenType() == JackTokens.SYMBOL) {
            if (checkSymbol("(")) {
                tokenizer.advance();
                compileExpression();
                if (checkSymbol(")")) {
                    tokenizer.advance();
                } else {
                    System.out.println("no closing bracket for term");
                }
            }

        } else {
            System.out.printf("illegal varName: %s\n", tokenizer.token());
            return;
        }
    }

    public void compileArrayTerm() {
        tokenizer.advance();
        compileExpression();

        if (!checkSymbol("]")) {
            System.out.println("No closing ] for the array expression");
        }
    }

    public void compileSubRoutineCall(String firstHalf) {
        String classRegx = "^[A-Z].*";
        boolean isClass;

        if (firstHalf.matches(classRegx)) isClass = true;
        else                              isClass = false;
        
        String fullSubName = null;
        int numOfArgs = 0;

        if (tokenizer.symbol().equals("(")) {
            fullSubName = className + "." + firstHalf;
            tokenizer.advance();
            writer.writePush("pointer", 0);
            numOfArgs = compileExpressionList(isClass);

            if (!checkSymbol(")")) {
                System.out.println("No closing ) for the expressionlist");
                return;
            }
        } else {
            tokenizer.advance();
            if (checkIdentifier()) {
                if (isClass) { 
                    fullSubName = firstHalf + "." + currentName;
                } else {
                    fullSubName = table.typeOf(firstHalf) + "." + currentName;
                    writer.writePush(table.kindOf(firstHalf), table.indexOf(firstHalf));
                }
            } else {
                System.out.println("illegal identifier for subroutine call");
                return;
            }

            tokenizer.advance();
            if (!checkSymbol("(")) {
                System.out.println("Expecting a open bracket in subroutine call");
                return;
            }

            tokenizer.advance();
            numOfArgs = compileExpressionList(isClass);

            if (!checkSymbol(")")) {
                System.out.printf("%s %d: is not closing ) for the expressionlist\n", tokenizer.token(), 
                        tokenizer.tokenType());
                return;
            }
        }
        if (fullSubName != null) writer.writeCall(fullSubName, numOfArgs); 
    }

    public int compileExpressionList(boolean isClass) {
        int argCounter = 1;
        if (isClass) argCounter = 0;

        if (!tokenizer.symbol().equals(")")) {
            compileExpression();
            argCounter++;
            while (checkSymbol(",")) {
                tokenizer.advance();
                compileExpression();
                argCounter++;
            }
        }

        return argCounter;
    }

    public static void main(String[] args) {
        String filename = args[0];
        CompilationEngine engine = new CompilationEngine(filename);
        engine.compileClass();
    }
}