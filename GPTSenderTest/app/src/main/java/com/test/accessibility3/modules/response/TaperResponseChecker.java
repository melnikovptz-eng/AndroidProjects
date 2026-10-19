package com.test.accessibility3.modules.response;

public class TaperResponseChecker {

    public static final String BEGIN =
            "⟦TAPER:BEGIN⟧";

    public static final String END =
            "⟦TAPER:END⟧";

    public enum Result {
        NOT_FOUND,
        LOADING,
        COMPLETE
    }

    public Result check(String text) {

        if (text == null ||
                text.isEmpty()) {

            return Result.NOT_FOUND;
        }

        boolean hasBegin =
                text.contains(BEGIN);

        boolean hasEnd =
                text.contains(END);

        if (!hasBegin) {
            return Result.NOT_FOUND;
        }

        if (!hasEnd) {
            return Result.LOADING;
        }

        int begin =
                text.indexOf(BEGIN);

        int end =
                text.indexOf(END);

        /*
         * END должен находиться
         * после BEGIN.
         */
        if (end <= begin) {
            return Result.LOADING;
        }

        return Result.COMPLETE;
    }

    public boolean isComplete(String text) {
        return check(text) == Result.COMPLETE;
    }

    public boolean isLoading(String text) {
        return check(text) == Result.LOADING;
    }

    public boolean isTaperResponse(String text) {
        Result result = check(text);

        return result == Result.LOADING ||
               result == Result.COMPLETE;
    }
}
