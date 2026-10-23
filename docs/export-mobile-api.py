"""Export the running 8080 API as OpenAPI, Postman and a full existing-route reference."""
import json
from pathlib import Path
import re
import sys
import urllib.request

directory = Path(__file__).resolve().parent
base = sys.argv[1].rstrip('/') if len(sys.argv) > 1 else 'http://127.0.0.1:8080'
with urllib.request.urlopen(base + '/v3/api-docs', timeout=15) as response:
    api = json.load(response)
assert '/api/v1/mobile/risk-tasks/{id}/media-links' in api['paths'], 'Restart the updated Java service before exporting'
api['servers'] = [{'url': 'http://{host}:8080', 'description': '服务器地址；公网部署时替换为HTTPS域名',
                   'variables': {'host': {'default': '192.168.0.111'}}}]
(directory / 'mobile-api.openapi.json').write_text(json.dumps(api, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

schemas = api.get('components', {}).get('schemas', {})

def schema(value):
    if '$ref' in value:
        return schemas.get(value['$ref'].split('/')[-1], {})
    return value

def type_name(value):
    if '$ref' in value:
        return value['$ref'].split('/')[-1]
    if value.get('type') == 'array':
        return 'array<' + type_name(value.get('items', {})) + '>'
    return value.get('type', 'object') + (('/' + value['format']) if value.get('format') else '')

def example(value, seen=()):
    if '$ref' in value:
        name = value['$ref'].split('/')[-1]
        if name in seen:
            return None
        return example(schemas.get(name, {}), (*seen, name))
    if 'example' in value:
        return value['example']
    if 'default' in value:
        return value['default']
    if value.get('enum'):
        return value['enum'][0]
    kind = value.get('type')
    if kind == 'object' or 'properties' in value:
        return {key: example(field, seen) for key, field in value.get('properties', {}).items()}
    if kind == 'array':
        return []
    if kind in ('number', 'integer'):
        return 0
    if kind == 'boolean':
        return False
    return ''

permissions = {}
source = directory.parent / 'src/main/java/com/hc/drown/module'
for controller in source.rglob('*Controller.java'):
    content = controller.read_text(encoding='utf-8')
    base_match = re.search(r'@RequestMapping\((.*?)\)\s', content, re.S)
    if not base_match:
        continue
    roots = re.findall(r'"([^"]+)"', base_match.group(1))
    methods = list(re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\("([^"]*)"\))?', content))
    for i, match in enumerate(methods):
        block = content[match.end():methods[i + 1].start() if i + 1 < len(methods) else len(content)]
        permission = re.search(r'@PreAuthorize\("([^"]+)"\)', block)
        for root in roots:
            permissions[(match.group(1).upper(), root + (match.group(2) or ''))] = permission.group(1) if permission else '登录；未另设方法权限'
for path in ('/api/v1/auth/login', '/api/v1/auth/register'):
    permissions[('POST', path)] = '公开，无需Bearer'

collection = {
    'info': {'name': '黄村乡防溺水 · 移动端与现有业务 API',
             'description': '先设置baseUrl、username、password并执行登录。新增风险任务使用mobile前缀。写请求应选择实际测试记录后按需执行。完整字段、媒体签名和错误语义见mobile-api.md及OpenAPI。',
             'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
    'auth': {'type': 'bearer', 'bearer': [{'key': 'token', 'value': '{{token}}', 'type': 'string'}]},
    'variable': [{'key': key, 'value': value} for key, value in {
        'baseUrl': 'http://192.168.0.111:8080', 'username': '', 'password': '', 'token': '',
        'taskId': '1', 'id': '1', 'deviceId': '1', 'expectedStatus': 'PENDING',
        'snapshotUrl': '', 'videoUrl': ''}.items()],
    'item': [],
}
groups = {}
reference = ['# 现有业务 API 完整索引', '',
             '由当前运行的 `/v3/api-docs` 和控制器权限注解生成。新增风险任务的完整接入合同见 [移动端接口文档](mobile-api.md)。', '',
             '所有路径使用 8080。除登录/注册、现有公开资源及有效签名的任务媒体外，需要 Bearer。此处列出现有监管业务；类型的完整嵌套结构可在 [OpenAPI](mobile-api.openapi.json) 的 components.schemas 查看。', '']
operation_count = 0
for path, methods in sorted(api['paths'].items()):
    for method, operation in sorted(methods.items()):
        if method not in {'get', 'post', 'put', 'delete', 'patch', 'head', 'options'}:
            continue
        operation_count += 1
        title = operation.get('summary') or operation.get('operationId', path)
        mobile = path.startswith('/api/v1/mobile/risk-tasks')
        group = '01 新增移动端风险任务' if mobile else (operation.get('tags') or ['其他'])[0]
        parameters = operation.get('parameters', [])
        route = re.sub(r'\{([^}]+)\}', lambda m: '{{' + ('taskId' if 'risk-tasks' in path else m.group(1)) + '}}', path)
        queries = []
        for parameter in parameters:
            if parameter.get('in') != 'query':
                continue
            field = parameter.get('schema', {})
            value = example(field)
            queries.append({'key': parameter['name'], 'value': str(value).lower() if isinstance(value, bool) else str(value or ''),
                            'disabled': not parameter.get('required', False) and parameter['name'] not in {'pageNum', 'pageSize'},
                            'description': parameter.get('description', '')})
        raw = '{{baseUrl}}' + route
        active = [q['key'] + '=' + q['value'] for q in queries if not q['disabled']]
        if active:
            raw += '?' + '&'.join(active)
        request = {'method': method.upper(), 'header': [],
                   'url': {'raw': raw, 'host': ['{{baseUrl}}'], 'path': route.strip('/').split('/'), 'query': queries},
                   'description': operation.get('description', '') + '\n权限：' + permissions.get((method.upper(), path), '按控制器鉴权')}
        for parameter in parameters:
            if parameter.get('in') == 'header':
                request['header'].append({'key': parameter['name'], 'value': 'bytes=0-1023' if parameter['name'].lower() == 'range' else '',
                                          'disabled': not parameter.get('required', False)})
        body_content = operation.get('requestBody', {}).get('content', {})
        body_schema = body_content.get('application/json', {}).get('schema')
        if body_schema:
            body = example(body_schema)
            if path == '/api/v1/auth/login':
                body = {'username': '{{username}}', 'password': '{{password}}'}
                request['auth'] = {'type': 'noauth'}
            if path == '/api/v1/auth/register':
                request['auth'] = {'type': 'noauth'}
            if 'risk-tasks/' in path and path.endswith('/status'):
                body = {'status': 'PROCESSED', 'expectedStatus': '{{expectedStatus}}', 'note': '已现场核验'}
            request['header'].append({'key': 'Content-Type', 'value': 'application/json'})
            request['body'] = {'mode': 'raw', 'raw': json.dumps(body, ensure_ascii=False, indent=2), 'options': {'raw': {'language': 'json'}}}
        item = {'name': method.upper() + ' ' + path + ' · ' + title, 'request': request}
        script = None
        if path == '/api/v1/auth/login':
            script = 'const r = pm.response.json(); if (r.code === 200 && r.data?.token) pm.collectionVariables.set("token", r.data.token);'
        elif mobile and path == '/api/v1/mobile/risk-tasks' and method == 'get':
            script = 'const r = pm.response.json(); const t = r.data?.records?.[0]; if (t) { pm.collectionVariables.set("taskId", String(t.id)); pm.collectionVariables.set("expectedStatus", t.status); }'
        elif mobile and path.endswith('/media-links'):
            script = 'const r = pm.response.json(); if (r.code === 200) { pm.collectionVariables.set("snapshotUrl", r.data.snapshotUrl || ""); pm.collectionVariables.set("videoUrl", r.data.videoUrl || ""); }'
        if script:
            item['event'] = [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': [script]}}]
        groups.setdefault(group, []).append(item)
        if 'risk-tasks' in path:
            continue
        reference.extend([f'## {method.upper()} `{path}`', '', title, '',
                          '**权限**：' + permissions.get((method.upper(), path), '登录，具体权限见运行接口约束'), ''])
        if parameters:
            reference.extend(['| 参数 | 位置 | 类型 | 必填 | 默认/说明 |', '| --- | --- | --- | --- | --- |'])
            for param in parameters:
                field = param.get('schema', {})
                description = (str(field.get('default', '')) + ' ' + param.get('description', '')).strip().replace('|', '\\|').replace('\n', ' ')
                reference.append(f"| `{param['name']}` | {param['in']} | {type_name(field)} | {'是' if param.get('required') else '否'} | {description} |")
            reference.append('')
        if body_schema:
            resolved = schema(body_schema)
            reference.extend(['**JSON 请求体**：`' + type_name(body_schema) + '`', '', '| 字段 | 类型 | 必填 | 说明 |', '| --- | --- | --- | --- |'])
            for name, field in resolved.get('properties', {}).items():
                description = field.get('description', '').replace('|', '\\|').replace('\n', ' ')
                reference.append(f"| `{name}` | {type_name(field)} | {'是' if name in resolved.get('required', []) else '否'} | {description} |")
            reference.append('')
        response_content = operation.get('responses', {}).get('200', {}).get('content', {})
        response_schema = response_content.get('*/*', response_content.get('application/json', {})).get('schema', {})
        if response_schema:
            reference.extend(['**响应模型**：`' + type_name(response_schema) + '`；JSON 包含 `code`、`message`、`data`。', ''])
collection['item'] = [{'name': key, 'item': value} for key, value in sorted(groups.items())]
collection['item'].insert(1, {'name': '02 不带Bearer的短时媒体链接（先获取media-links）', 'item': [
    {'name': '读取短时截图链接', 'request': {'method': 'GET', 'auth': {'type': 'noauth'}, 'url': '{{baseUrl}}{{snapshotUrl}}'}},
    {'name': '读取短时视频链接', 'request': {'method': 'GET', 'auth': {'type': 'noauth'}, 'url': '{{baseUrl}}{{videoUrl}}'}},
]})
(directory / 'mobile-api.postman_collection.json').write_text(json.dumps(collection, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
(directory / 'existing-api-reference.md').write_text('\n'.join(reference) + '\n', encoding='utf-8')
print(json.dumps({'openapi_paths': len(api['paths']), 'operations': operation_count, 'postman_groups': len(collection['item'])}))
