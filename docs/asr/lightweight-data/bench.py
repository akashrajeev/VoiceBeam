import json,subprocess,time,re,pathlib,wave,numpy as np,sherpa_onnx,sys
P=pathlib.Path('/tmp/vb-bench'); L=pathlib.Path('/tmp/vb-light'); rows=json.load(open(P/'manifest.json'))[:6]
def words(s):return re.findall(r'[a-z0-9]+',s.lower().replace('’',"'"))
def dist(a,b):
 d=list(range(len(b)+1))
 for i,x in enumerate(a,1):
  n=[i]
  for j,y in enumerate(b,1):n.append(min(n[-1]+1,d[j]+1,d[j-1]+(x!=y)))
  d=n
 return d[-1]
kind=sys.argv[1]; results=[]
if kind=='zipformer-large':
 m=L/'sherpa-onnx-streaming-zipformer-en-2023-06-26';files=[m/f for f in ['encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx','decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx','joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx','tokens.txt']]
 r=sherpa_onnx.OnlineRecognizer.from_transducer(tokens=str(files[3]),encoder=str(files[0]),decoder=str(files[1]),joiner=str(files[2]),num_threads=2,enable_endpoint_detection=False)
elif kind=='nemo80':
 m=L/'sherpa-onnx-nemo-streaming-fast-conformer-ctc-en-80ms-int8';files=[m/'model.int8.onnx',m/'tokens.txt'];r=sherpa_onnx.OnlineRecognizer.from_nemo_ctc(tokens=str(files[1]),model=str(files[0]),num_threads=2)
elif kind=='moonshine-tiny':
 m=L/'sherpa-onnx-moonshine-tiny-en-int8';files=[m/f for f in ['preprocess.onnx','encode.int8.onnx','uncached_decode.int8.onnx','cached_decode.int8.onnx','tokens.txt']];r=sherpa_onnx.OfflineRecognizer.from_moonshine(*map(str,files),num_threads=2)
else:files=[pathlib.Path('/tmp/ggml-'+kind+'.bin')]
for row in rows:
 with wave.open(str(P/(row['ID']+'.wav'))) as f:a=np.frombuffer(f.readframes(f.getnframes()),dtype='<i2').astype(np.float32)/32768
 t=time.monotonic()
 if kind in ['zipformer-large','nemo80']:
  s=r.create_stream()
  for off in range(0,len(a),1600):
   s.accept_waveform(16000,a[off:off+1600])
   while r.is_ready(s):r.decode_stream(s)
  s.accept_waveform(16000,np.zeros(8000,dtype=np.float32));s.input_finished()
  while r.is_ready(s):r.decode_stream(s)
  text=r.get_result(s)
 elif kind=='moonshine-tiny':
  s=r.create_stream();s.accept_waveform(16000,a);r.decode_stream(s);text=s.result.text
 else:
  prefix=L/(kind+'-'+row['ID'])
  with open(str(prefix)+'.log','w') as log:subprocess.run(['/tmp/whisper.cpp/build/bin/whisper-cli','-m',str(files[0]),'-f',str(P/(row['ID']+'.wav')),'-l','en','-t','2','-nt','-otxt','-of',str(prefix)],stdout=log,stderr=log,check=True,timeout=100)
  text=pathlib.Path(str(prefix)+'.txt').read_text()
 elapsed=time.monotonic()-t
 results.append(dict(id=row['ID'],text=text,errors=dist(words(row['Transcript']),words(text)),words=len(words(row['Transcript'])),audioSec=len(a)/16000,elapsedSec=elapsed))
 json.dump(dict(model=kind,modelBytes=sum(f.stat().st_size for f in files),results=results),open(L/(kind+'.json'),'w'),indent=2)
print(kind,round(sum(x['errors'] for x in results)/sum(x['words'] for x in results)*100,2),round(sum(x['elapsedSec'] for x in results)/sum(x['audioSec'] for x in results),3),sum(f.stat().st_size for f in files),flush=True)
