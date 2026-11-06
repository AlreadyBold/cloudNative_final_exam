"""
약품명 정규화 : 검색할 때 DB 의 item_name_normalized 와 같은 규칙으로 검색어를 바꾼다.
  예) "타이레놀정 500밀리그람(아세트아미노펜)" → "타이레놀정500mg"
tools/build_catalog_sql.py 의 normalize_name 과 같은 규칙이다. (둘 중 하나를 바꾸면 다른 쪽도 같이 바꿔야 한다)
"""
import re

INNERMOST_BRACKETS = re.compile(r"\([^()]*\)|\[[^\[\]]*]|\{[^{}]*}|<[^<>]*>")
UNCLOSED_BRACKET = re.compile(r"[(\[{<].*$", re.DOTALL)
STRAY_CLOSING_BRACKET = re.compile(r"[)\]}>]")
WHITESPACE = re.compile(r"\s+")
EXPORT_NAME = re.compile(r"(?<=.)수출명.*$", re.DOTALL)


def normalize_name(name):
    if not name:
        return ""
    current = name
    while True:  # 겹친 괄호도 안쪽부터 반복해서 지운다
        previous = current
        current = INNERMOST_BRACKETS.sub("", previous)
        if current == previous:
            break
    current = UNCLOSED_BRACKET.sub("", current)
    current = STRAY_CLOSING_BRACKET.sub("", current)
    current = WHITESPACE.sub("", current)
    current = EXPORT_NAME.sub("", current)
    return (current.lower()
            .replace("밀리그램", "mg")
            .replace("밀리그람", "mg")
            .replace("마이크로그램", "μg")
            .replace("밀리리터", "ml"))


def escape_like(text):
    """LIKE 검색어의 특수문자(%, _, \\)를 글자 그대로 찾도록 바꾼다"""
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
