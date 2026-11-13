# Lists app texts that are not in app/src/main/assets/lang/strings.tsv yet.
# Run from the repo root:  python3 tools/lang_missing.py
import re,glob,os,sys,json
SKIP={'SmsParser.java','NotifParser.java','Notices.java','Voice.java','StatementParser.java','Reconcile.java','ReasonLogic.java','BackupCrypt.java','Backup.java','Updater.java','Contacts.java','Locator.java','PayListener.java','SmsReceiver.java','Engine.java','Ledger.java','Store.java','Txn.java','Account.java','Plan.java','CalcMath.java','GoalMath.java','SaveMath.java','Fmt.java','Lang.java','MoneyApp.java','BgJob.java','UsageWatch.java','Lock.java','Smart.java','Habits.java','Limits.java','Setup.java'}
def tokenize(s):
    toks=[];i=0;n=len(s)
    while i<n:
        c=s[i]
        if s.startswith('//',i): j=s.find('\n',i); i=n if j<0 else j; continue
        if s.startswith('/*',i): j=s.find('*/',i+2); i=n if j<0 else j+2; continue
        if c=='"':
            j=i+1; buf=''
            while j<n and s[j]!='"':
                if s[j]=='\\': buf+=s[j:j+2]; j+=2; continue
                buf+=s[j]; j+=1
            toks.append(('S',buf)); i=j+1; continue
        if c=="'":
            j=i+1
            while j<n and s[j]!="'":
                j+= 2 if s[j]=='\\' else 1
            toks.append(('X',s[i:j+1])); i=j+1; continue
        if c.isspace(): i+=1; continue
        if c in '(){}[],;?:+': toks.append(('P',c)); i+=1; continue
        m=re.match(r'[A-Za-z_0-9.$@]+|==|!=|<=|>=|&&|\|\||->|::|[-*/%<>=!&|^~]',s[i:])
        if m: toks.append(('W',m.group(0))); i+=len(m.group(0)); continue
        toks.append(('W',c)); i+=1
    return toks
def unesc(t): return t.replace('\\n','\n').replace('\\"','"').replace("\\'","'").replace('\\\\','\\')
keys=[]
def region(toks):
    # split region tokens at top-level delimiters , ; ? : and process each by '+'
    depth=0;start=0;parts=[]
    for k,(t,v) in enumerate(toks):
        if t=='P' and v in '([{': depth+=1
        elif t=='P' and v in ')]}': depth-=1
        elif depth==0 and t=='P' and v in ',;?:': parts.append(toks[start:k]); start=k+1
        elif depth==0 and t=='W' and v in ('=','->','return','=='): parts.append(toks[start:k]); start=k+1
    parts.append(toks[start:])
    for p in parts: chain(p)
def chain(toks):
    depth=0;ops=[];cur=[]
    for t,v in toks:
        if t=='P' and v in '([{': depth+=1
        if t=='P' and v in ')]}': depth-=1
        if depth==0 and t=='P' and v=='+': ops.append(cur); cur=[]; continue
        cur.append((t,v))
    ops.append(cur)
    hasLit=any(len(o)==1 and o[0][0]=='S' for o in ops)
    if hasLit:
        key=''
        for o in ops:
            if len(o)==1 and o[0][0]=='S': key+=unesc(o[0][1])
            elif o: key+='{}'
        keys.append(key)
    # recurse into groups
    for o in ops:
        i=0
        while i<len(o):
            t,v=o[i]
            if t=='P' and v in '([{':
                d=1;j=i+1
                while j<len(o) and d>0:
                    if o[j][0]=='P' and o[j][1] in '([{': d+=1
                    elif o[j][0]=='P' and o[j][1] in ')]}': d-=1
                    j+=1
                region(o[i+1:j-1]); i=j; continue
            i+=1
src=sys.argv[1] if len(sys.argv)>1 else 'app/src/main/java/com/debangshu/money'
out={}
for f in sorted(glob.glob(src+'/*.java')):
    b=os.path.basename(f)
    if b in SKIP: continue
    keys=[]
    toks=tokenize(open(f).read())
    region(toks)
    good=[]
    for k in keys:
        if len(re.findall(r'[A-Za-z]',k))<3: continue
        if re.match(r'^[a-z_0-9.]+$',k) or k.startswith(('com.','android.','http','#','image/','application/','text/','content:')): continue
        if '\\b' in k or '(?' in k or '[^' in k or '\\d' in k: continue
        if re.match(r'^[a-z][A-Za-z0-9_]*$',k): continue   # identifiers / pref keys
        good.append(k)
    out[b]=sorted(set(good),key=good.index)
have=set()
for line in open('app/src/main/assets/lang/strings.tsv',encoding='utf-8'):
    if line.startswith('#') or not line.strip(): continue
    have.add(line.split('\t')[0].replace('\\n','\n'))
n=0
for b,v in out.items():
    miss=[k for k in v if k.replace('\\n','\n') not in have and k.replace('\\n','\n').strip() not in have]
    if miss: print('\n## '+b+' ('+str(len(miss))+')')
    for k in miss: print('  '+k); n+=1
print('\n'+str(n)+' texts still in English only')
