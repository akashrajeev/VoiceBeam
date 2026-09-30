import json,subprocess,time,re,pathlib,wave,numpy as np,sherpa_onnx
P=pathlib.Path('/tmp/vb-bench'); manifest=json.load(open(P/'manifest.json'))
def words(s):return re.findall(r"[a-z0-9]+",s.lower().replace('’',"'"))
def distance(a,b):
 d=list(range(len(b)+1))
 for i,x in enumerate(a,1):
  n=[i]
  for j,y in enumerate(b,1):n.append(min(n[-1]+1,d[j]+1,d[j-1]+(x!=y)))
  d=n
 return d[-1]
model=pathlib.Path('/tmp/vb-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17')
r=sherpa_onnx.OnlineRecognizer.from_transducer(tokens=str(model/'tokens.txt'),encoder=str(model/'encoder-epoch-99-avg-1.int8.onnx'),decoder=str(model/'decoder-epoch-99-avg-1.onnx'),joiner=str(model/'joiner-epoch-99-avg-1.int8.onnx'),num_threads=2,sample_rate=16000,feature_dim=80,decoding_method='greedy_search',enable_endpoint_detection=False)
results=[]
for row in manifest:
 with wave.open(str(P/(row['ID']+'.wav'))) as f:a=np.frombuffer(f.readframes(f.getnframes()),dtype='<i2').astype(np.float32)/32768
 t=time.monotonic();s=r.create_stream()
 for off in range(0,len(a),1600):
  s.accept_waveform(16000,a[off:off+1600])
  while r.is_ready(s):r.decode_stream(s)
 s.accept_waveform(16000,np.zeros(8000,dtype=np.float32));s.input_finished()
 while r.is_ready(s):r.decode_stream(s)
 text=r.get_result(s);sec=time.monotonic()-t
 results.append(dict(model='zipformer20m',id=row['ID'],text=text,errors=distance(words(row['Transcript']),words(text)),words=len(words(row['Transcript'])),audioSec=len(a)/16000,elapsedSec=sec))
json.dump(results,open(P/'zipformer.json','w'),indent=2)
for model,weight in [('small-q5_1','/tmp/ggml-small-q5_1.bin'),('turbo-q5_0','/tmp/ggml-large-v3-turbo-q5_0.bin')]:
 results=[]
 for row in manifest[:6]:
  prefix=P/(model+'-'+row['ID']);t=time.monotonic()
  cmd=['/tmp/whisper.cpp/build/bin/whisper-cli','-m',weight,'-f',str(P/(row['ID']+'.wav')),'-l','en','-t','2','-nt','-otxt','-of',str(prefix)]
  with open(str(prefix)+'.log','w') as log:out=subprocess.run(cmd,stdout=log,stderr=log,timeout=90)
  if out.returncode:continue
  text=pathlib.Path(str(prefix)+'.txt').read_text();sec=time.monotonic()-t
  results.append(dict(model=model,id=row['ID'],text=text,errors=distance(words(row['Transcript']),words(text)),words=len(words(row['Transcript'])),audioSec=row['Speech_Duration_seconds'],elapsedSec=sec))
  json.dump(results,open(P/(model+'.json'),'w'),indent=2)
