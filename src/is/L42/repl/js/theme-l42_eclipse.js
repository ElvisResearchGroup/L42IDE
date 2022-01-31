define("ace/theme/l42_eclipse",["require","exports","module","ace/lib/dom"], function(require, exports, module) {

exports.isDark = false;
exports.cssClass = "ace-l42-eclipse";
exports.cssText = ".ace-l42-eclipse .ace_gutter {\
background:"+ Styling.GUTTER_BACKGROUND + ";\
border-right: 1px solid rgb(159, 159, 159);\
color: "+ Styling.GUTTER_COLOR +";\
}\
.ace-l42-eclipse .ace_fold {\
background-color: "+ Styling.FOLD_BACKGROUND +";\
}\
.ace-l42-eclipse .ace_cursor {\
color: "+ Styling.CURSOR_COLOR +";\
}\
.ace-l42-eclipse .ace_keyword{\
color: "+ Styling.KEYWORD_COLOR +";\
}\
.ace-l42-eclipse .ace_errorHighlight {\
color: "+ Styling.ERROR_COLOR +";\
background-color: "+ Styling.ERROR_HIGHLIGHT+";\
font-weight: bold\
}\
.ace-l42-eclipse .ace_reuselibrary {\
font-weight: bold;\
font-style: italic;\
}\
.ace-l42-eclipse .ace_string {\
color: "+ Styling.STRING_COLOR +";\
}\
.ace-l42-eclipse .ace_comment {\
color: "+ Styling.COMMENT_COLOR +";\
}\
.ace-l42-eclipse .ace_marker-layer .ace_selection {\
background: "+ Styling.SELECTION_HIGHLIGHT +";\
}\
.ace-l42-eclipse .ace_marker-layer .ace_bracket {\
margin: -1px 0 0 -1px;\
border: 1px solid "+ Styling.BRACKET_BORDER +";\
}\
.ace-l42-eclipse .ace_active-line {\
background: "+ Styling.ACTIVE_LINE_HIGHLIGHT +";\
}\
.ace-l42-eclipse .ace_gutter-active-line {\
background-color : "+ Styling.ACTIVE_LINE_GUTTER_HIGHLIGHT +";\
}\
.ace-l42-eclipse .ace_marker-layer .ace_selected-word {\
border: 1px solid rgb(181, 213, 255);\
}\
.ace-l42-eclipse .ace_upperIdentifiers {\
font-weight: bold\
}\
.ace-l42-eclipse .ace_methodParameters {\
color: "+ Styling.PARAMETER_COLOR +";\
font-style: italic;\
}\
.ace-l42-eclipse .ace_objectCall {\
color: "+ Styling.OBJECT_COLOR +";\
font-style: italic;\
}";

var dom = require("../lib/dom");
dom.importCssString(exports.cssText, exports.cssClass);
});
