/*
 * Copyright (c) 2010-2016, Sikuli.org, sikulix.com
 * Released under the MIT License.
 *
 */
package org.sikuli.basics;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

import com.sikulix.core.Content;
import com.sikulix.core.SX;
//import com.sikulix.scripting.JythonHelper;

/**
 * Debug is a utility class that wraps println statements and allows more or less command line
 * output to be turned on.<br> <br> For debug messages only ( Debug.log() ):<br> Use system
 * property: sikuli.Debug to set the debug level (default = 1)<br> On the command line, use
 * -Dsikuli.Debug=n to set it to level n<br> -Dsikuli.Debug will disable any debug messages <br>
 * (which is equivalent to using Settings.Debuglogs = false)<br> <br> It prints if the level
 * number is less than or equal to the currently set DEBUG_LEVEL.<br> <br> For messages
 * ActionLogs, InfoLogs see Settings<br> <br> You might send all messages generated by this
 * class to a file:<br>-Dsikuli.Logfile=pathname (no path given: SikuliLog.txt in working
 * folder)<br> This can be restricted to Debug.user only (others go to System.out):<br>
 * -Dsikuli.LogfileUser=pathname (no path given: UserLog.txt in working folder)<br>
 *
 * You might redirect info, action, error and debug messages to your own logger object<br>
 * Start with setLogger() and then define with setLoggerXyz() the redirection targets
 *
 * This solution is NOT threadsafe !!!
 */
public class Debug {

  private static int DEBUG_LEVEL = 0;
	private static boolean loggerRedirectSupported = true;
	public static boolean shouldLogJython = false;
	private long _beginTime = 0;
  private String _message;
  private String _title = null;
  private static PrintStream printout = null;
  private static PrintStream printoutuser = null;
  private static final DateFormat df =
          DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
  public static String logfile;

	private static Object privateLogger = null;
	private static boolean privateLoggerPrefixAll = true;
	private static Method privateLoggerUser = null;
	private static String privateLoggerUserName = "";
	private static String privateLoggerUserPrefix = "";
	private static Method privateLoggerInfo = null;
	private static String privateLoggerInfoName = "";
	private static final String infoPrefix = "info";
	private static String privateLoggerInfoPrefix = "[" + infoPrefix + "] ";
	private static Method privateLoggerAction = null;
	private static String privateLoggerActionName = "";
	private static final String actionPrefix = "log";
	private static String privateLoggerActionPrefix = "[" + actionPrefix + "] ";
	private static Method privateLoggerError = null;
	private static String privateLoggerErrorName = "";
	private static final String errorPrefix = "error";
	private static String privateLoggerErrorPrefix = "[" + errorPrefix + "] ";
	private static Method privateLoggerDebug = null;
	private static String privateLoggerDebugName = "";
	private static final String debugPrefix = "debug";
	private static String privateLoggerDebugPrefix = "";
	private static boolean isJython;
	private static boolean isJRuby;
  private static Object scriptRunner = null;

  private static boolean searchHighlight = false;

	private static PrintStream redirectedOut = null, redirectedErr = null;

  static {
    String debug = System.getProperty("sikuli.Debug");
    if (debug != null && "".equals(debug)) {
      DEBUG_LEVEL = 0;
			SX.setOption("DebugLogs", "false");
    } else {
      try {
        DEBUG_LEVEL = Integer.parseInt(debug);
        if (DEBUG_LEVEL > 0) {
					SX.setOption("DebugLogs", "true");
				} else {
					SX.setOption("DebugLogs", "false");
        }
      } catch (NumberFormatException numberFormatException) {
      }
    }
    setLogFile(null);
    setUserLogFile(null);
  }

  public static void init() {
    if (DEBUG_LEVEL > 0) {
      logx(DEBUG_LEVEL, "Debug.init: from sikuli.Debug: on: %d", DEBUG_LEVEL);
    }
  }

  public static void highlightOn() {
    searchHighlight = true;
		SX.setOption("Highlight", "true");
  }

  public static void highlightOff() {
    searchHighlight = false;
		SX.setOption("Highlight", "false");
  }

  public static boolean shouldHighlight() {
    return searchHighlight;
  }

	/**
	 * A logger object that is intended, to get Sikuli's log messages per redirection
	 * @param logger the logger object
	 */
	public static void setLogger(Object logger) {
		if (!doSetLogger(logger)) return;
		privateLoggerPrefixAll = true;
    logx(3, "Debug: setLogger %s", logger);
	}

	/**
	 * same as setLogger(), but the Sikuli prefixes are omitted in all redirected messages
	 * @param logger the logger object
	 */
	public static void setLoggerNoPrefix(Object logger) {
		if (!doSetLogger(logger)) return;
		privateLoggerPrefixAll = false;
	}

	private static boolean doSetLogger(Object logger) {
		String className = logger.getClass().getName();
		isJython = className.contains("org.python");
		isJRuby = className.contains("org.jruby");
		if ( isJRuby ) {
			logx(3, "Debug: setLogger: given instance's class: %s", className);
			error("setLogger: not yet supported in JRuby script");
			loggerRedirectSupported=false;
			return false;
		}
		privateLogger = logger;
		return true;
	}

	/**
	 * sets the redirection for all message types user, info, action, error and debug
	 * must be the name of an instance method of the previously defined logger and<br>
	 * must accept exactly one string parameter, that contains the message text
	 * @param mAll name of the method where the message should be sent
	 * @return true if the method is available false otherwise	 */
	public static boolean setLoggerAll(String mAll) {
		if (!loggerRedirectSupported) {
			logx(3, "Debug: setLoggerAll: logger redirect not supported");
			return false;
		}
		if (privateLogger != null) {
      logx(3, "Debug.setLoggerAll: %s", mAll);
			boolean success = true;
			success &= setLoggerUser(mAll);
			success &= setLoggerInfo(mAll);
			success &= setLoggerAction(mAll);
			success &= setLoggerError(mAll);
			success &= setLoggerDebug(mAll);
			return success;
		}
		return false;
	}

	private static boolean doSetLoggerCallback(String mName, CallbackType type) {
		if (privateLogger == null) {
			error("Debug: setLogger: no logger specified yet");
			return false;
		}
		if (!loggerRedirectSupported) {
			logx(3, "Debug: setLogger: %s (%s) logger redirect not supported", mName, type);
		}
		if (isJython) {
			Object[] args = new Object[]{privateLogger, mName, type.toString()};
			//TODO ScriptingHelper
//			if (!JythonHelper.get().checkCallback(args)) {
//				logx(3, "Debug: setLogger: Jython: checkCallback returned: %s", args[0]);
//				return false;
//			}
		}
		try {
			if (type == CallbackType.INFO) {
				if ( !isJython && !isJRuby ) {
					privateLoggerInfo = privateLogger.getClass().getMethod(mName, new Class[]{String.class});
				}
				privateLoggerInfoName = mName;
				return true;
			} else if (type == CallbackType.ACTION) {
				if ( !isJython && !isJRuby ) {
					privateLoggerAction = privateLogger.getClass().getMethod(mName, new Class[]{String.class});
				}
				privateLoggerActionName = mName;
				return true;
			} else if (type == CallbackType.ERROR) {
				if ( !isJython && !isJRuby ) {
					privateLoggerError = privateLogger.getClass().getMethod(mName, new Class[]{String.class});
				}
				privateLoggerErrorName = mName;
				return true;
			} else if (type == CallbackType.DEBUG) {
				if ( !isJython && !isJRuby ) {
					privateLoggerDebug = privateLogger.getClass().getMethod(mName, new Class[]{String.class});
				}
				privateLoggerDebugName = mName;
				return true;
			} else if (type == CallbackType.USER) {
				if ( !isJython && !isJRuby ) {
					privateLoggerUser = privateLogger.getClass().getMethod(mName, new Class[]{String.class});
				}
				privateLoggerUserName = mName;
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			error("Debug: setLoggerInfo: redirecting to %s failed: \n%s", mName, e.getMessage());
		}
		return false;
	}

	/**
	 * specify the target method for redirection of Sikuli's user log messages [user]<br>
	 * must be the name of an instance method of the previously defined logger and<br>
	 * must accept exactly one string parameter, that contains the info message
	 * @param mUser name of the method where the message should be sent
	 * <br>reset to default logging by either null or empty string
	 * @return true if the method is available false otherwise
	 */
	public static boolean setLoggerUser(String mUser) {
		if (mUser == null || mUser.isEmpty()) {
			privateLoggerUserName = "";
			return true;
		}
		return doSetLoggerCallback(mUser, CallbackType.USER);
	}

	/**
	 * specify the target method for redirection of Sikuli's info messages [info]<br>
	 * must be the name of an instance method of the previously defined logger and<br>
	 * must accept exactly one string parameter, that contains the info message
	 * @param mInfo name of the method where the message should be sent
	 * <br>reset to default logging by either null or empty string
	 * @return true if the method is available false otherwise
	 */
	public static boolean setLoggerInfo(String mInfo) {
		if (mInfo == null || mInfo.isEmpty()) {
			privateLoggerInfoName = "";
			return true;
		}
		return doSetLoggerCallback(mInfo, CallbackType.INFO);
	}

	/**
	 * specify the target method for redirection of Sikuli's action messages [log]<br>
	 * must be the name of an instance method of the previously defined logger and<br>
	 * must accept exactly one string parameter, that contains the info message
	 * @param mAction name of the method where the message should be sent
	 * <br>reset to default logging by either null or empty string
	 * @return true if the method is available false otherwise
	 */
	public static boolean setLoggerAction(String mAction) {
		if (mAction == null || mAction.isEmpty()) {
			privateLoggerActionName = "";
			return true;
		}
		return doSetLoggerCallback(mAction, CallbackType.ACTION);
	}

	/**
	 * specify the target method for redirection of Sikuli's error messages [error]<br>
	 * must be the name of an instance method of the previously defined logger and<br>
	 * must accept exactly one string parameter, that contains the info message
	 * @param mError name of the method where the message should be sent
	 * <br>reset to default logging by either null or empty string
	 * @return true if the method is available false otherwise
	 */
	public static boolean setLoggerError(String mError) {
		if (mError == null || mError.isEmpty()) {
			privateLoggerErrorName = "";
			return true;
		}
		return doSetLoggerCallback(mError, CallbackType.ERROR);
	}

	/**
	 * specify the target method for redirection of Sikuli's debug messages [debug]<br>
	 * must be the name of an instance method of the previously defined logger and<br>
	 * must accept exactly one string parameter, that contains the info message
	 * @param mDebug name of the method where the message should be sent
	 * <br>reset to default logging by either null or empty string
	 * @return true if the method is available false otherwise
	 */
	public static boolean setLoggerDebug(String mDebug) {
		if (mDebug == null || mDebug.isEmpty()) {
			privateLoggerDebugName = "";
			return true;
		}
		return doSetLoggerCallback(mDebug, CallbackType.DEBUG);
	}

	public static void saveRedirected(PrintStream rdo, PrintStream rde) {
		redirectedOut = rdo;
		redirectedErr = rde;
	}

	public static void out(String msg) {
		if (redirectedOut != null && DEBUG_LEVEL > 2) {
			redirectedOut.println(msg);
		}
	}

	/**
	 * specify, where the logs should be written:<br>
	 * null - use from property sikuli.Logfile
	 * empty - use SikuliLog.txt in working folder
	 * not empty - use given filename
	 * @param fileName null, empty or absolute filename
	 * @return success
	 */
	public static boolean setLogFile(String fileName) {
    if (fileName == null) {
      fileName = System.getProperty("sikuli.Logfile");
    }
    if (fileName != null) {
      if ("".equals(fileName)) {
        if (SX.isOption("isMacApp")) {
          fileName = "SikulixLog.txt";
        } else {
          fileName = Content.slashify(System.getProperty("user.dir"), true) + "SikulixLog.txt";
        }
      }
      try {
        logfile = fileName;
        if (printout != null) {
          printout.close();
        }
        printout = new PrintStream(fileName);
        log(3, "Debug: setLogFile: " + fileName);
        return true;
      } catch (Exception ex) {
        System.out.printf("[Error] Logfile %s not accessible - check given path", fileName);
        System.out.println();
        return false;
      }
    }
    return false;
  }

	/**
	 * does Sikuli log go to a file?
	 * @return true if yes, false otherwise
	 */
	public static boolean isLogToFile() {
    return (printout != null);
  }

	/**
	 * specify, where the user logs (Debug.user) should be written:<br>
	 * null - use from property sikuli.LogfileUser
	 * empty - use UserLog.txt in working folder
	 * not empty - use given filename
	 * @param fileName null, empty or absolute filename
	 * @return success
	 */
	public static boolean setUserLogFile(String fileName) {
    if (fileName == null) {
      fileName = System.getProperty("sikuli.LogfileUser");
    }
    if (fileName != null) {
      if ("".equals(fileName)) {
        if (SX.isOption("isMacApp")) {
          fileName = "UserLog.txt";
        } else {
          fileName = Content.slashify(System.getProperty("user.dir"), true) + "UserLog.txt";
        }
      }
      try {
        if (printoutuser != null) {
          printoutuser.close();
        }
        printoutuser = new PrintStream(fileName);
        log(3, "Debug: setLogFile: " + fileName);
        return true;
      } catch (FileNotFoundException ex) {
        System.out.printf("[Error] User logfile %s not accessible - check given path", fileName);
        System.out.println();
        return false;
      }
    }
    return false;
  }

	/**
	 * does user log go to a file?
	 * @return true if yes, false otherwise
	 */
  public static boolean isUserLogToFile() {
    return (printoutuser != null);
  }

  /**
   *
   * @return current debug level
   */
  public static int getDebugLevel() {
    return DEBUG_LEVEL;
  }

  /**
   * set debug level to default level
   *
   * @return default level
   */
  public static int setDebugLevel() {
    setDebugLevel(0);
    return DEBUG_LEVEL;
  }

  /**
   * set debug level to given value
   *
   * @param level value
   */
  public static void setDebugLevel(int level) {
    DEBUG_LEVEL = level;
    if (DEBUG_LEVEL > 0) {
			SX.setOption("DebugLogs", "true");
    } else {
			SX.setOption("DebugLogs", "false");
    }
  }

  public static void on(int level) {
    setDebugLevel(level);
  }

  public static void on(String level) {
    setDebugLevel(level);
  }

  public static boolean is(int level) {
    return DEBUG_LEVEL >= level;
  }

  public static int is() {
    return DEBUG_LEVEL;
  }

  public static void off() {
    setDebugLevel(0);
  }

  /**
   * set debug level to given number value as string (ignored if invalid)
   *
   * @param level valid number string
   */
  public static void setDebugLevel(String level) {
    try {
      DEBUG_LEVEL = Integer.parseInt(level);
      if (DEBUG_LEVEL > 0) {
				SX.setOption("DebugLogs", "true");
      } else {
				SX.setOption("DebugLogs", "false");
      }
    } catch (NumberFormatException e) {
    }
  }

	private static boolean doRedirect(CallbackType type, String pre, String message, Object... args) {
		boolean success = false;
		String error = "";
		if (privateLogger != null) {
			String prefix = "", pln = "";
			Method plf = null;
			if (type == CallbackType.INFO && !privateLoggerInfoName.isEmpty()) {
				prefix = privateLoggerPrefixAll ? privateLoggerInfoPrefix : "";
				plf = privateLoggerInfo;
				pln = privateLoggerInfoName;
			} else if (type == CallbackType.ACTION && !privateLoggerActionName.isEmpty()) {
				prefix = privateLoggerPrefixAll ? privateLoggerActionPrefix : "";
				plf = privateLoggerAction;
				pln = privateLoggerActionName;
			} else if (type == CallbackType.ERROR && !privateLoggerErrorName.isEmpty()) {
				prefix = privateLoggerPrefixAll ? privateLoggerErrorPrefix : "";
				plf = privateLoggerError;
				pln = privateLoggerErrorName;
			} else if (type == CallbackType.DEBUG && !privateLoggerDebugName.isEmpty()) {
				prefix = privateLoggerPrefixAll ?
								(privateLoggerDebugPrefix.isEmpty() ? pre : privateLoggerDebugPrefix) : "";
				plf = privateLoggerDebug;
				pln = privateLoggerDebugName;
			} else if (type == CallbackType.USER && !privateLoggerUserName.isEmpty()) {
				prefix = privateLoggerPrefixAll ?
									(privateLoggerUserPrefix.isEmpty() ? pre : privateLoggerUserPrefix) : "";
				plf = privateLoggerUser;
				pln = privateLoggerUserName;
			}
			if (!pln.isEmpty()) {
				String msg = null;
				if (args == null) {
					msg = prefix + message;
				} else {
					msg = String.format(prefix + message, args);
				}
				if (isJython) {
					success = false; //TODO JythonHelper.get().runLoggerCallback(new Object[]{privateLogger, pln, msg});
				} else if (isJRuby) {
					success = false;
				} else {
					try {
						plf.invoke(privateLogger,
										new Object[]{msg});
						return true;
					} catch (Exception e) {
						error = ": " + e.getMessage();
						success = false;
					}
				}
				if (!success) {
					Debug.error("calling (%s) logger.%s failed - resetting to default%s", type, pln, error);
					if (type == CallbackType.INFO) {
						privateLoggerInfoName = "";
					} else if (type == CallbackType.ACTION) {
						privateLoggerActionName = "";
					} else if (type == CallbackType.ERROR) {
						privateLoggerErrorName = "";
					} else if (type == CallbackType.DEBUG) {
						privateLoggerDebugName = "";
					} else if (type == CallbackType.USER) {
						privateLoggerUserName = "";
					}
				}
			}
		}
		return success;
	}
	/**
   * Sikuli messages from actions like click, ...<br> switch on/off: Settings.ActionLogs
   *
   * @param message String or format string (String.format)
   * @param args to use with format string
   */
  public static void action(String message, Object... args) {
    if (SX.isOption("ActionLogs")) {
      if (doRedirect(CallbackType.ACTION, "", message, args)) {
        return;
      }
      if (is(3)) {
        logx(3, message, args);
      } else {
        log(-1, actionPrefix, message, args);
      }
    }
  }

  /**
   * use Debug.action() instead
   * @param message String or format string (String.format)
   * @param args to use with format string
   * @deprecated
   */
  @Deprecated
  public static void history(String message, Object... args) {
    action(message, args);
  }

  /**
   * informative Sikuli messages <br> switch on/off: Settings.InfoLogs
   *
   * @param message String or format string (String.format)
   * @param args to use with format string
   */
  public static void info(String message, Object... args) {
    if (SX.isOption("InfoLogs")) {
			if (doRedirect(CallbackType.INFO, "", message, args)) {
				return;
			}
      log(-1, infoPrefix, message, args);
    }
    if (is(3)) {
      logx(3, message, args);
    }
  }

  /**
   * Sikuli error messages<br> switch on/off: always on
   *
   * @param message String or format string (String.format)
   * @param args to use with format string
   */
	public static void error(String message, Object... args) {
		if (doRedirect(CallbackType.ERROR, "", message, args)) {
			return;
		}
		log(-1, errorPrefix, message, args);
	}

  /**
   * Sikuli messages to use in tests<br> switch on/off: always on
   *
   * @param message String or format string (String.format)
   * @param args to use with format string
   */
  public static void test(String message, Object... args) {
		if (message.contains("#returned#")) {
			message = message.replace("#returned#", "returned: " +
							((Boolean) args[0] ? "true" : "false"));
			args = Arrays.copyOfRange(args, 1, args.length);
		}
    log(-1, "test", message, args);
  }

  /**
   * Sikuli debug messages with default level<br> switch on/off: Settings.DebugLogs (off) and/or
   * -Dsikuli.Debug
   *
   * @param message String or format string (String.format)
   * @param args to use with format string
   */
  public static void log(String message, Object... args) {
    log(0, message, args);
  }

	public static boolean logJython() {
		return logJython(null);
	}

	public static boolean logJython(Boolean state) {
		if (null != state) {
			shouldLogJython = state;
		}
		return shouldLogJython;
	}

	public static void logj(String message, Object... args) {
		if (shouldLogJython) {
			log(0, "Jython: " + message, args);
		}
	}

	/**
   * messages given by the user<br> switch on/off: Settings.UserLogs<br> depending on
   * Settings.UserLogTime, the prefix contains a timestamp <br> the user prefix (default "user")
   * can be set: Settings,UserLogPrefix
   *
   * @param message String or format string (String.format)
   * @param args to use with format string
   */
  public static void user(String message, Object... args) {
    if (SX.isOption("UserLogs")) {
      if (SX.isOption("UserLogTime")) {
//TODO replace the hack -99 to filter user logs
        log(-99, String.format("%s (%s)",
                SX.getOption("UserLogPrefix"), df.format(new Date())), message, args);
      } else {
        log(-99, String.format("%s", SX.getOption("UserLogPrefix")), message, args);
      }
    }
  }

  /**
   * Sikuli debug messages with level<br> switch on/off: Settings.DebugLogs (off) and/or
   * -Dsikuli.Debug
   *
   * @param level value
   * @param message String or format string (String.format)
   * @param args to use with format string
   */
  public static void log(int level, String message, Object... args) {
    if (SX.isOption("DebugLogs")) {
      log(level, debugPrefix, message, args);
    }
  }

	/**
	 * INTERNAL USE: special debug messages
	 * @param level value
	 * @param message text or format string
	 * @param args for use with format string
	 */
	public static String logx(int level, String message, Object... args) {
    String sout = "";
    if (level == -1 || level == -100) {
      sout = log(level, errorPrefix, message, args);
    } else if (level == -2) {
      sout = log(level, actionPrefix, message, args);
    } else if (level == -3) {
      sout = log(level, "", message, args);
    } else {
      sout = log(level, debugPrefix, message, args);
    }
    return sout;
  }

  public static String logp(String msg, Object... args) {
    String out = String.format(msg, args);
    System.out.println(out);
    return out;
  }

  private static synchronized String log(int level, String prefix, String message, Object... args) {
//TODO replace the hack -99 to filter user logs
    String sout = "";
    String stime = "";
    if (level <= DEBUG_LEVEL) {
      if (level == 3) {
        if (message.startsWith("TRACE: ")) {
          if (!SX.isOption("TraceLogs")) {
            return "";
          }
        }
      }
      if (SX.isOption("LogTime") && level != -99) {
        stime = String.format(" (%s)", df.format(new Date()));
      }
			if (!prefix.isEmpty()) {
        prefix = "[" + prefix + stime + "] ";
      }
      sout = String.format(message, args);
      boolean isRedirected = false;
			if (level > -99) {
				isRedirected = doRedirect(CallbackType.DEBUG, prefix, sout, null);
			} else if (level == -99) {
				isRedirected = doRedirect(CallbackType.USER, prefix, sout, null);
			}
      if (!isRedirected) {
        if (level == -99 && printoutuser != null) {
          printoutuser.print(prefix + sout);
          printoutuser.println();
        } else if (printout != null) {
          printout.print(prefix + sout);
          printout.println();
        } else {
          System.out.print(prefix + sout);
          System.out.println();
        }
        if (level == -1 || level == -100 || level > 2) {
          out(prefix + sout);
        }
      }
    }
    return prefix + sout;
  }

  /**
   * Sikuli profiling messages<br> switch on/off: Settings.ProfileLogs, default off
   *
   * @param message String or format string
   * @param args to use with format string
   */
  public static void profile(String message, Object... args) {
    if (SX.isOption("ProfileLogs")) {
      log(-1, "profile", message, args);
    }
  }

	/**
	 * profile convenience: entering a method
   * @param message String or format string
   * @param args to use with format string
	 */
	public static void enter(String message, Object... args) {
    profile("entering: " + message, args);
  }

	/**
	 * profile convenience: exiting a method
   * @param message String or format string
   * @param args to use with format string
	 */
	public static void exit(String message, Object... args) {
    profile("exiting: " + message, args);
  }

	/**
	 * start timer
	 * <br>log output depends on Settings.ProfileLogs
	 * @return timer
	 */
	public static Debug startTimer() {
    return startTimer("");
  }

	/**
	 * start timer with a message
	 * <br>log output depends on Settings.ProfileLogs
   * @param message String or format string
   * @param args to use with format string
	 * @return timer
	 */
  public static Debug startTimer(String message, Object... args) {
    Debug timer = new Debug();
    timer.startTiming(message, args);
    return timer;
  }

  /**
   * stop timer and print timer message
 	 * <br>log output depends on Settings.ProfileLogs
  *
   * @return the time in msec
   */
  public long end() {
    if (_title == null) {
      return endTiming(_message, false, new Object[0]);
    } else {
      return endTiming(_title, false, new Object[0]);
    }
  }

  /**
   * lap timer and print message with timer message
	 * <br>log output depends on Settings.ProfileLogs
   *
	 * @param message String or format string
   * @return the time in msec
   */
  public long lap(String message) {
    if (_title == null) {
      return endTiming("(" + message + ") " + _message, true, new Object[0]);
    } else {
      return endTiming("(" + message + ") " + _title, true, new Object[0]);
    }
  }

	private void startTiming(String message, Object... args) {
    int pos;
    if ((pos = message.indexOf("\t")) < 0) {
      _title = null;
      _message = message;
    } else {
      _title = message.substring(0, pos);
      _message = message.replace("\t", " ");
    }
    if (!"".equals(_message)) {
      profile("TStart: " + _message, args);
    }
    _beginTime = (new Date()).getTime();
  }

  private long endTiming(String message, boolean isLap, Object... args) {
    if (_beginTime == 0) {
      profile("TError: timer not started (%s)", message);
      return -1;
    }
    long t = (new Date()).getTime();
    long dt = t - _beginTime;
    if (!isLap) {
      _beginTime = 0;
    }
    if (!"".equals(message)) {
      profile(String.format((isLap ? "TLap:" : "TEnd") +
              " (%.3f sec): ", (float) dt / 1000) + message, args);
    }
    return dt;
  }

	private static enum CallbackType {
		INFO, ACTION, ERROR, DEBUG, USER;
	}
}
